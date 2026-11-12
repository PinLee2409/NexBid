package com.nexbid.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionLifecycleEvent;
import com.nexbid.auction.AuctionService;
import com.nexbid.auction.ReserveEvents;
import com.nexbid.bid.BidService;
import com.nexbid.bid.OutbidEvent;
import com.nexbid.mail.MailFacts;
import com.nexbid.mail.MailKind;
import com.nexbid.mail.Mailer;
import com.nexbid.payment.PaymentDue;
import com.nexbid.payment.PaymentEvents;
import com.nexbid.payment.PaymentService;
import com.nexbid.payment.SecondChanceEvents;
import com.nexbid.watchlist.WatchlistService;

/**
 * EN: Decides who hears about what (guide §29, spec §15, §18, §38). Event handlers are called by
 *     NotificationConsumer as events arrive from Kafka, long after the bid that caused them committed.
 * VI: Quyết định ai được báo về chuyện gì (guide §29, spec §15, §18, §38). Các hàm xử lý sự kiện do
 *     NotificationConsumer gọi khi sự kiện tới từ Kafka, sau khi lượt trả giá gây ra nó đã commit từ lâu.
 */
@Component
class NotificationTriggers {

    private final NotificationService notifications;
    private final AuctionService auctions;
    private final BidService bids;
    private final WatchlistService watchlist;
    private final PaymentService payments;
    private final Mailer mailer;
    private final Duration headsUp;
    private final Duration paymentReminder;
    private final Duration paymentWindow;

    NotificationTriggers(
            NotificationService notifications,
            AuctionService auctions,
            BidService bids,
            WatchlistService watchlist,
            PaymentService payments,
            Mailer mailer,
            @Value("${nexbid.notification.heads-up}") Duration headsUp,
            @Value("${nexbid.notification.payment-reminder}") Duration paymentReminder,
            @Value("${nexbid.payment.window}") Duration paymentWindow) {

        this.notifications = notifications;
        this.auctions = auctions;
        this.bids = bids;
        this.watchlist = watchlist;
        this.payments = payments;
        this.headsUp = headsUp;
        this.paymentReminder = paymentReminder;
        this.mailer = mailer;
        this.paymentWindow = paymentWindow;
    }

    /**
     * EN: Whoever lost the lead is told (spec §38) — decided once the bid and its auto answers settled, so
     *     someone whose auto bid won it straight back hears nothing.
     * VI: Ai mất vị trí dẫn đầu thì được báo (spec §38) — quyết định khi lượt trả giá và các auto bid đáp trả
     *     đã ổn định, nên người được auto bid giành lại ngay sẽ không nghe gì.
     */
    @Transactional
    void onOutbid(OutbidEvent event) {
        String title = auctions.lotTitleOf(event.auctionId());
        String message = title + ": someone bid " + money(event.currentPrice()) + ".";
        Instant endsAt = auctions.participantSummariesOf(List.of(event.auctionId()))
                .get(event.auctionId()).auction().endTime();
        MailFacts facts = MailFacts.lot(event.auctionId(), title, event.currentPrice());

        for (UUID outbid : event.outbid()) {
            notifications.notify(outbid, NotificationType.OUTBID, "You've been outbid", message,
                    event.auctionId(), event.at());
            mailer.sendOutbid(outbid, facts, endsAt, event.at());
        }
    }

    /**
     * EN: At the close, the winner hears they won and every other bidder hears they did not. An extension
     *     is told to the lot's bidders and watchers, except whoever caused it.
     * VI: Lúc đóng phiên, người thắng được báo là đã thắng, mọi người trả giá khác được báo là đã thua. Khi gia
     *     hạn thì báo người đã trả giá và người theo dõi lô, trừ người gây ra nó.
     */
    @Transactional
    void onLifecycleChange(AuctionLifecycleEvent event) {
        if (AuctionLifecycleEvent.EXTENDED.equals(event.type())) {
            onExtended(event);
            return;
        }
        if (!AuctionLifecycleEvent.ENDED.equals(event.type())) {
            return;
        }

        UUID auctionId = event.auctionId();
        UUID winner = event.winnerId();
        String title = auctions.lotTitleOf(auctionId);
        String price = money(event.currentPrice());

        if (winner == null) {
            // EN: Unsold. With bids, it closed below the reserve; that has its own notices.
            // VI: Không bán được. Có lượt trả giá thì là đóng dưới giá sàn; chuyện đó có thông báo riêng.
            return;
        }
        notifications.notify(winner, NotificationType.AUCTION_WON,
                "You won", title + ": winning bid " + price + ".", auctionId, event.endTime());
        // EN: The payment opened at the close with the same window. / VI: Khoản thanh toán mở lúc đóng phiên với cùng thời hạn.
        mailer.send(winner, MailKind.AUCTION_WON, MailFacts.lot(auctionId, title, event.currentPrice())
                .withUntil(event.endTime().plus(paymentWindow)));

        for (UUID bidder : bids.biddersOf(auctionId)) {
            if (!bidder.equals(winner)) {
                notifications.notify(bidder, NotificationType.AUCTION_LOST,
                        "Auction lost", title + ": sold to another bidder for " + price + ".", auctionId, event.endTime());
            }
        }
    }

    /**
     * EN: Anti-sniping can extend a lot several times in its last minute; the index keeps it to one unread
     *     notice per person, so the bell rings again only after they looked.
     * VI: Anti-sniping có thể gia hạn một lô nhiều lần trong phút cuối; index giữ mỗi người chỉ một thông báo
     *     chưa đọc, nên chuông chỉ rung lại sau khi họ đã xem.
     */
    private void onExtended(AuctionLifecycleEvent event) {
        UUID auctionId = event.auctionId();
        Set<UUID> audience = new LinkedHashSet<>(bids.biddersOf(auctionId));
        audience.addAll(watchlist.watchersOf(auctionId));
        audience.remove(event.leaderId());

        String message = auctions.lotTitleOf(auctionId) + ": a last-minute bid extended the auction.";
        for (UUID person : audience) {
            notifications.notify(person, NotificationType.AUCTION_EXTENDED, "Auction extended", message,
                    auctionId, Instant.now());
        }
    }

    /** EN: The payer hears it went through (spec §18). / VI: Người trả được báo là đã thành công (spec §18). */
    @Transactional
    void onPaymentSucceeded(PaymentEvents.Succeeded event) {
        String title = auctions.lotTitleOf(event.auctionId());
        notifications.notify(event.userId(), NotificationType.PAYMENT_SUCCESS, "Payment successful",
                title + ": " + money(event.amount()) + " paid.", event.auctionId(), event.at());
        mailer.send(event.userId(), MailKind.PAYMENT_RECEIPT, MailFacts.lot(event.auctionId(), title, event.amount()));
    }

    /**
     * EN: The payer hears the window closed (spec §18), and the sale is cancelled for both sides of it.
     * VI: Người trả được báo đã quá hạn (spec §18), và giao dịch bị huỷ được báo cho cả hai bên.
     */
    @Transactional
    void onPaymentExpired(PaymentEvents.Expired event) {
        UUID auctionId = event.auctionId();
        String title = auctions.lotTitleOf(auctionId);

        notifications.notify(event.userId(), NotificationType.PAYMENT_EXPIRED, "Payment window closed",
                title + ": not paid in time, so the sale was cancelled.", auctionId, event.at());
        notifications.notify(event.userId(), NotificationType.AUCTION_CANCELLED, "Auction cancelled",
                title + ": the sale to you was cancelled.", auctionId, event.at());
        UUID seller = auctions.sellerOf(auctionId);
        notifications.notify(seller, NotificationType.AUCTION_CANCELLED, "Sale cancelled",
                title + ": the winner did not pay in time, so the sale was cancelled.", auctionId, event.at());
        MailFacts facts = MailFacts.lot(auctionId, title, event.amount());
        mailer.send(event.userId(), MailKind.PAYMENT_EXPIRED, facts);
        mailer.send(seller, MailKind.SALE_CANCELLED, facts);
    }

    /**
     * EN: Closed below the reserve: the highest bidder and the seller hear who decides and by when; everyone else lost.
     * VI: Đóng dưới giá sàn: người trả cao nhất và người bán được báo ai quyết định và tới khi nào; những người khác đã thua.
     */
    @Transactional
    void onReserveNotMet(ReserveEvents.NotMet event) {
        UUID auctionId = event.auctionId();
        String title = auctions.lotTitleOf(auctionId);
        String window = describe(Duration.between(event.at(), event.decideBy()));

        notifications.notify(event.leaderId(), NotificationType.RESERVE_NOT_MET, "Reserve not met",
                title + ": your bid of " + money(event.price()) + " is below the seller's reserve. The seller has "
                        + window + " to accept it.", auctionId, event.at());
        notifications.notify(event.sellerId(), NotificationType.RESERVE_NOT_MET, "Reserve not met",
                title + ": the highest bid, " + money(event.price()) + ", is below your reserve. Accept it within "
                        + window + " or the lot goes unsold.", auctionId, event.at());
        for (UUID bidder : bids.biddersOf(auctionId)) {
            if (!bidder.equals(event.leaderId())) {
                notifications.notify(bidder, NotificationType.AUCTION_LOST, "Auction lost",
                        title + ": another bidder bid higher.", auctionId, event.at());
            }
        }
    }

    @Transactional
    void onReserveAccepted(ReserveEvents.Accepted event) {
        String title = auctions.lotTitleOf(event.auctionId());
        notifications.notify(event.buyerId(), NotificationType.AUCTION_WON, "You won",
                title + ": the seller accepted your bid of " + money(event.price()) + ".",
                event.auctionId(), event.at());
        mailer.send(event.buyerId(), MailKind.AUCTION_WON, MailFacts.lot(event.auctionId(), title, event.price())
                .withUntil(event.at().plus(paymentWindow)));
    }

    @Transactional
    void onReserveRefused(ReserveEvents.Refused event) {
        String title = auctions.lotTitleOf(event.auctionId());
        notifications.notify(event.leaderId(), NotificationType.AUCTION_LOST, "Not sold",
                title + ": the seller did not accept your bid of " + money(event.price()) + ".",
                event.auctionId(), event.at());
        if (event.lapsed()) {
            notifications.notify(event.sellerId(), NotificationType.RESERVE_NOT_MET, "Lot not sold",
                    title + ": the highest bid was not accepted in time, so the lot went unsold.",
                    event.auctionId(), event.at());
        }
    }

    /** EN: The runner-up hears the lot can still be theirs (spec §17). / VI: Người thứ hai được báo lô vẫn có thể là của họ (spec §17). */
    @Transactional
    void onOfferMade(SecondChanceEvents.Offered event) {
        String title = auctions.lotTitleOf(event.auctionId());
        mailer.send(event.buyerId(), MailKind.SECOND_CHANCE_OFFER,
                MailFacts.lot(event.auctionId(), title, event.amount()).withUntil(event.expiresAt()));
        notifications.notify(event.buyerId(), NotificationType.SECOND_CHANCE_OFFER, "Second chance",
                title + ": the winner did not pay. It can be yours for your bid of "
                        + money(event.amount()) + " if you accept within "
                        + describe(Duration.between(event.at(), event.expiresAt())) + ".",
                event.auctionId(), event.at());
    }

    /** EN: The seller hears the runner-up's answer, or that none came. / VI: Người bán được báo câu trả lời của người thứ hai, hoặc là không có. */
    @Transactional
    void onOfferAccepted(SecondChanceEvents.Accepted event) {
        notifications.notify(event.sellerId(), NotificationType.SECOND_CHANCE_ACCEPTED, "Offer accepted",
                auctions.lotTitleOf(event.auctionId()) + ": the next bidder accepted at " + money(event.amount())
                        + ". Their payment is now open.",
                event.auctionId(), event.at());
    }

    @Transactional
    void onOfferDeclined(SecondChanceEvents.Declined event) {
        String answer = event.lapsed() ? "did not answer in time" : "declined";
        notifications.notify(event.sellerId(), NotificationType.SECOND_CHANCE_DECLINED,
                event.lapsed() ? "Offer lapsed" : "Offer declined",
                auctions.lotTitleOf(event.auctionId()) + ": the next bidder " + answer
                        + ". The product is yours to list again.",
                event.auctionId(), event.at());
    }

    /**
     * EN: Reminds winners whose payment is due within the reminder window. Once per lot, like the heads-ups.
     * VI: Nhắc những người thắng có khoản thanh toán tới hạn trong khoảng nhắc. Mỗi lô một lần, như heads-up.
     */
    @Transactional
    int sendPaymentReminders(Instant now) {
        int sent = 0;
        for (PaymentDue due : payments.openDueBetween(now, now.plus(paymentReminder))) {
            String title = auctions.lotTitleOf(due.auctionId());
            int written = notifications.notify(due.userId(), NotificationType.PAYMENT_REQUIRED, "Payment due soon",
                    title + ": less than " + describe(paymentReminder) + " left to pay " + money(due.amount()) + ".",
                    due.auctionId(), now);
            // EN: Once per lot, like the notice: the index said whether this is the first.
            // VI: Mỗi lô một lần, như thông báo: index cho biết đây có phải lần đầu.
            if (written == 1) {
                mailer.send(due.userId(), MailKind.PAYMENT_REMINDER,
                        MailFacts.lot(due.auctionId(), title, due.amount()).withUntil(due.deadline()));
            }
            sent += written;
        }
        return sent;
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

    /** EN: "12 hours", or "30 minutes" below an hour. / VI: "12 hours", hoặc "30 minutes" nếu dưới một giờ. */
    private static String describe(Duration duration) {
        return duration.toHours() >= 1 ? duration.toHours() + " hours" : duration.toMinutes() + " minutes";
    }

    /** EN: "18,500,000 VND", as spec §38 writes it. / VI: "18,500,000 VND", đúng như spec §38 viết. */
    private static String money(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount) + " VND";
    }
}
