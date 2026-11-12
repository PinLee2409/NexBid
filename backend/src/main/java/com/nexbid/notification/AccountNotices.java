package com.nexbid.notification;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nexbid.auction.AuctionService;
import com.nexbid.mail.MailFacts;
import com.nexbid.mail.MailKind;
import com.nexbid.mail.Mailer;
import com.nexbid.order.OrderEvents;
import com.nexbid.user.SellerApplicationEvent;

/**
 * EN: Notices about the account itself: becoming a seller, and the reviews it gets. Written in the transaction that
 *     caused them rather than through Kafka: one notice each, with nothing else waiting on it.
 * VI: Thông báo về chính tài khoản: trở thành người bán, và các đánh giá nhận được. Ghi trong transaction gây ra
 *     chúng thay vì qua Kafka: mỗi việc một thông báo, không có gì khác chờ nó.
 */
@Component
class AccountNotices {

    private final NotificationService notifications;
    private final AuctionService auctions;
    private final Mailer mailer;

    AccountNotices(NotificationService notifications, AuctionService auctions, Mailer mailer) {
        this.notifications = notifications;
        this.auctions = auctions;
        this.mailer = mailer;
    }

    /**
     * EN: The buyer hears their order is on its way — by email only; the order page already shows the new status.
     * VI: Người mua được báo đơn hàng đang trên đường tới — chỉ qua email; trang đơn hàng vốn đã hiện trạng thái mới.
     */
    @EventListener
    void onShipped(OrderEvents.Shipped event) {
        mailer.send(event.buyerId(), MailKind.ORDER_SHIPPED,
                MailFacts.lot(event.auctionId(), auctions.lotTitleOf(event.auctionId()), null));
    }

    /** EN: The seller hears each review, with the stars. / VI: Người bán được báo mỗi đánh giá, kèm số sao. */
    @EventListener
    void onReview(OrderEvents.Reviewed event) {
        notifications.notify(event.sellerId(), NotificationType.NEW_REVIEW, "New review",
                auctions.lotTitleOf(event.auctionId()) + ": " + event.rating() + " of 5 stars from the buyer.",
                event.auctionId(), event.at());
    }

    /** EN: The applicant hears the decision; sending a request tells nobody. / VI: Người nộp được báo quyết định; gửi yêu cầu thì không báo ai. */
    @EventListener
    void onSellerApplication(SellerApplicationEvent event) {
        switch (event.status()) {
            case APPROVED -> {
                notifications.notify(event.userId(), NotificationType.SELLER_APPROVED, "You can sell now",
                        "Your request was approved. List your first item from the seller workspace.", null, event.at());
                mailer.send(event.userId(), MailKind.SELLER_APPROVED, MailFacts.none());
            }
            case REJECTED -> {
                notifications.notify(event.userId(), NotificationType.SELLER_REJECTED,
                        // EN: The reason alone: both are capped at 500 characters. / VI: Chỉ lý do: cả hai đều giới hạn 500 ký tự.
                        "Seller request declined", event.reason(), null, event.at());
                mailer.send(event.userId(), MailKind.SELLER_REJECTED, MailFacts.none().withReason(event.reason()));
            }
            case PENDING -> {
                // EN: The admin queue shows it. / VI: Hàng chờ của admin đã hiện nó.
            }
        }
    }
}
