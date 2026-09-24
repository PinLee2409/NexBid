package com.nexbid.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nexbid.auction.AuctionLifecycleEvent;
import com.nexbid.auction.AuctionService;
import com.nexbid.bid.BidService;
import com.nexbid.bid.OutbidEvent;
import com.nexbid.payment.PaymentEvents;
import com.nexbid.watchlist.WatchlistService;

/**
 * EN: Decides who hears about what (guide §29, spec §15, §18, §38). Runs after commit, on another thread,
 *     so a failed notice never costs a bid — and never holds two DB connections at once (that deadlocked).
 * VI: Quyết định ai được báo về chuyện gì. Chạy sau commit, trên luồng khác, nên thông báo hỏng không làm
 *     mất lượt trả giá — và không bao giờ giữ hai kết nối DB cùng lúc (từng gây kẹt pool).
 */
@Component
class NotificationTriggers {

    private final NotificationService notifications;
    private final AuctionService auctions;
    private final BidService bids;
    private final WatchlistService watchlist;
    private final Duration headsUp;

    NotificationTriggers(
            NotificationService notifications,
            AuctionService auctions,
            BidService bids,
            WatchlistService watchlist,
            @Value("${nexbid.notification.heads-up}") Duration headsUp) {

        this.notifications = notifications;
        this.auctions = auctions;
        this.bids = bids;
        this.watchlist = watchlist;
        this.headsUp = headsUp;
    }

    /**
     * EN: Whoever lost the lead is told (spec §38) — decided once the bid and its auto answers settled, so
     *     someone whose auto bid won it straight back hears nothing.
     * VI: Ai mất vị trí dẫn đầu thì được báo (spec §38) — quyết định khi lượt trả giá và các auto bid đáp trả
     *     đã ổn định, nên người được auto bid giành lại ngay sẽ không nghe gì.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onOutbid(OutbidEvent event) {
        String message = auctions.lotTitleOf(event.auctionId()) + ": someone bid " + money(event.currentPrice()) + ".";

        for (UUID outbid : event.outbid()) {
            notifications.notify(outbid, NotificationType.OUTBID, "You've been outbid", message,
                    event.auctionId(), event.at());
        }
    }

    /**
     * EN: At the close, the winner hears they won and every other bidder hears they did not.
     * VI: Lúc đóng phiên, người thắng được báo là đã thắng, mọi người trả giá khác được báo là đã thua.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onLifecycleChange(AuctionLifecycleEvent event) {
        if (!AuctionLifecycleEvent.ENDED.equals(event.type())) {
            return;
        }

        UUID auctionId = event.auctionId();
        UUID winner = event.winnerId();
        String title = auctions.lotTitleOf(auctionId);
        String price = money(event.currentPrice());

        if (winner != null) {
            notifications.notify(winner, NotificationType.AUCTION_WON,
                    "You won", title + ": winning bid " + price + ".", auctionId, event.endTime());
        }

        for (UUID bidder : bids.biddersOf(auctionId)) {
            if (!bidder.equals(winner)) {
                notifications.notify(bidder, NotificationType.AUCTION_LOST,
                        "Auction lost", title + ": sold to another bidder for " + price + ".", auctionId, event.endTime());
            }
        }
    }

    /** EN: The payer hears it went through (spec §18). / VI: Người trả được báo là đã thành công (spec §18). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onPaymentSucceeded(PaymentEvents.Succeeded event) {
        notifications.notify(event.userId(), NotificationType.PAYMENT_SUCCESS, "Payment successful",
                auctions.lotTitleOf(event.auctionId()) + ": " + money(event.amount()) + " paid.",
                event.auctionId(), event.at());
    }

    /** EN: The payer hears the window closed and the lot is gone (spec §18). / VI: Người trả được báo đã quá hạn và lô không còn (spec §18). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void onPaymentExpired(PaymentEvents.Expired event) {
        notifications.notify(event.userId(), NotificationType.PAYMENT_EXPIRED, "Payment window closed",
                auctions.lotTitleOf(event.auctionId()) + ": not paid in time, so the sale was cancelled.",
                event.auctionId(), event.at());
    }

    /**
     * EN: Tells watchers a lot opens or closes soon (spec §15). Safe to run as often as the scheduler
     *     likes: each person hears about each lot once, enforced by the database, not by memory.
     * VI: Báo cho người theo dõi rằng một lô sắp mở hoặc sắp đóng (spec §15). Scheduler chạy bao nhiêu lần
     *     cũng an toàn: mỗi người chỉ nghe về mỗi lô một lần, do database đảm bảo chứ không nhờ trí nhớ.
     */
    @Transactional
    int sendHeadsUps(Instant now) {
        Instant until = now.plus(headsUp);
        int sent = 0;

        for (UUID auctionId : auctions.openingBetween(now, until)) {
            String title = auctions.lotTitleOf(auctionId);
            for (UUID watcher : watchlist.watchersOf(auctionId)) {
                sent += notifications.notify(watcher, NotificationType.AUCTION_STARTING,
                        "Starting soon", title + ": bidding opens soon.", auctionId, now);
            }
        }

        for (UUID auctionId : auctions.closingBetween(now, until)) {
            String title = auctions.lotTitleOf(auctionId);
            for (UUID watcher : watchlist.watchersOf(auctionId)) {
                sent += notifications.notify(watcher, NotificationType.AUCTION_ENDING,
                        "Ending soon", title + ": bidding closes soon.", auctionId, now);
            }
        }

        return sent;
    }

    /** EN: "18,500,000 VND", as spec §38 writes it. / VI: "18,500,000 VND", đúng như spec §38 viết. */
    private static String money(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount) + " VND";
    }
}
