package com.nexbid.notification;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.ReserveEvents;
import com.nexbid.mail.MailFacts;
import com.nexbid.mail.MailKind;
import com.nexbid.mail.Mailer;
import com.nexbid.order.OrderEvents;
import com.nexbid.payment.PaymentEvents;
import com.nexbid.payout.PayoutEvents;
import com.nexbid.question.QuestionEvents;
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
    private final Duration paymentWindow;

    AccountNotices(NotificationService notifications, AuctionService auctions, Mailer mailer,
            @Value("${nexbid.payment.window}") Duration paymentWindow) {
        this.notifications = notifications;
        this.auctions = auctions;
        this.mailer = mailer;
        this.paymentWindow = paymentWindow;
    }

    /**
     * EN: Buy Now closes the lot on the spot: the buyer hears what to pay and by when, the seller that it sold.
     * VI: Mua ngay đóng lô tức thì: người mua được báo phải trả bao nhiêu và trước khi nào, người bán được báo đã bán.
     */
    @EventListener
    void onBoughtNow(ReserveEvents.BoughtNow event) {
        MailFacts facts = MailFacts.lot(event.auctionId(), auctions.lotTitleOf(event.auctionId()), event.price())
                .withUntil(event.at().plus(paymentWindow));
        mailer.send(event.buyerId(), MailKind.AUCTION_WON, facts);
        mailer.send(auctions.sellerOf(event.auctionId()), MailKind.LOT_SOLD, facts);
    }

    /** EN: The seller hears of a dispute while it can still help. / VI: Người bán được báo về khiếu nại khi còn kịp hỗ trợ. */
    @EventListener
    void onDisputed(OrderEvents.Disputed event) {
        String title = auctions.lotTitleOf(event.auctionId());
        notifications.notify(event.sellerId(), NotificationType.DISPUTE_OPENED, "Buyer opened a dispute",
                title + ": " + reasonEnglish(event.reason()) + ". The order is on hold while NexBid reviews it.",
                event.auctionId(), event.at());
        mailer.send(event.sellerId(), MailKind.DISPUTE_OPENED,
                MailFacts.lot(event.auctionId(), title, null).withReason(reasonText(event.reason())));
    }

    /**
     * EN: Both sides hear the decision. A refunded buyer already gets the refund email, so only a rejection is
     *     written to the buyer here.
     * VI: Cả hai bên được báo quyết định. Người mua được hoàn tiền đã có email hoàn tiền, nên ở đây chỉ gửi người mua
     *     khi khiếu nại bị bác.
     */
    @EventListener
    void onDisputeResolved(OrderEvents.DisputeResolved event) {
        String title = auctions.lotTitleOf(event.auctionId());
        String note = event.note() == null ? "" : " Note: " + event.note();
        if (event.refunded()) {
            // EN: The buyer's own notice is the refund's, with the transfer in it. / VI: Thông báo của người mua là thông báo hoàn tiền, kèm lần chuyển khoản.
            notifications.notify(event.sellerId(), NotificationType.DISPUTE_SELLER_RESOLVED, "Buyer refunded",
                    clip(title + ": after reviewing the dispute, NexBid refunded the buyer." + note), event.auctionId(),
                    event.at());
        } else {
            notifications.notify(event.buyerId(), NotificationType.DISPUTE_RESOLVED, "Dispute not upheld",
                    clip(title + ": NexBid reviewed your dispute and the sale stands." + note), event.auctionId(),
                    event.at());
            notifications.notify(event.sellerId(), NotificationType.DISPUTE_SELLER_RESOLVED, "Dispute closed",
                    clip(title + ": NexBid turned the dispute down and the sale stands." + note), event.auctionId(),
                    event.at());
        }

        MailFacts facts = MailFacts.lot(event.auctionId(), title, null).withReason(event.note());
        if (event.refunded()) {
            mailer.send(event.sellerId(), MailKind.DISPUTE_SELLER_REFUNDED, facts);
        } else {
            mailer.send(event.buyerId(), MailKind.DISPUTE_REJECTED, facts);
            mailer.send(event.sellerId(), MailKind.DISPUTE_SELLER_REJECTED, facts);
        }
    }

    /** EN: The seller hears their money was sent. / VI: Người bán được báo tiền đã được chuyển. */
    @EventListener
    void onPaidOut(PayoutEvents.Paid event) {
        notifications.notify(event.sellerId(), NotificationType.PAYOUT_SENT, "Payout sent",
                clip(java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(event.amount())
                        + " VND was transferred to your bank account. Reference: " + event.reference() + "."),
                null, event.at());
        mailer.send(event.sellerId(), MailKind.SELLER_PAYOUT,
                new MailFacts(null, null, event.amount(), null, event.reference(), null, null));
    }

    private static String reasonEnglish(String reason) {
        return switch (reason) {
            case "NOT_RECEIVED" -> "not received";
            case "NOT_AS_DESCRIBED" -> "not as described";
            case "DAMAGED" -> "arrived damaged";
            default -> "another problem";
        };
    }

    /**
     * EN: A notice holds 500 characters; an admin's note can be longer, and the email carries it whole.
     * VI: Thông báo chứa 500 ký tự; ghi chú của admin có thể dài hơn, và email mang nó đầy đủ.
     */
    private static String clip(String message) {
        return message.length() <= 500 ? message : message.substring(0, 499) + "…";
    }

    private static String reasonText(String reason) {
        return switch (reason) {
            case "NOT_RECEIVED" -> "Chưa nhận được hàng · Not received";
            case "NOT_AS_DESCRIBED" -> "Không đúng mô tả · Not as described";
            case "DAMAGED" -> "Hàng bị hư hỏng · Damaged";
            default -> "Lý do khác · Other";
        };
    }

    /**
     * EN: The buyer hears the money was transferred back, where to, and the bank's reference to look for.
     * VI: Người mua được báo tiền đã được chuyển trả, về tài khoản nào, và mã giao dịch để đối chiếu.
     */
    @EventListener
    void onRefunded(PaymentEvents.Refunded event) {
        String title = auctions.lotTitleOf(event.auctionId());
        notifications.notify(event.userId(), NotificationType.ORDER_REFUNDED, "Refund sent",
                clip(title + ": " + java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(event.amount())
                        + " VND was transferred to " + event.account() + ". Reference: " + event.reference() + "."),
                event.auctionId(), event.at());
        mailer.send(event.userId(), MailKind.PAYMENT_REFUNDED, MailFacts.lot(event.auctionId(), title, event.amount())
                .withReason(event.reference()).withTracking(event.account(), null));
    }

    /**
     * EN: The buyer hears their order is on its way — by email only; the order page already shows the new status.
     * VI: Người mua được báo đơn hàng đang trên đường tới — chỉ qua email; trang đơn hàng vốn đã hiện trạng thái mới.
     */
    @EventListener
    void onShipped(OrderEvents.Shipped event) {
        mailer.send(event.buyerId(), MailKind.ORDER_SHIPPED,
                MailFacts.lot(event.auctionId(), auctions.lotTitleOf(event.auctionId()), null)
                        .withTracking(event.carrier(), event.trackingNumber()));
    }

    /** EN: The seller hears each question while the lot is open. / VI: Người bán được báo mỗi câu hỏi khi lô còn mở. */
    @EventListener
    void onQuestion(QuestionEvents.Asked event) {
        String title = auctions.lotTitleOf(event.auctionId());
        notifications.notify(event.sellerId(), NotificationType.NEW_QUESTION, "New question",
                clip(title + ": " + event.body()), event.auctionId(), event.at());
        mailer.send(event.sellerId(), MailKind.LOT_QUESTION,
                MailFacts.lot(event.auctionId(), title, null).withReason(event.body()));
    }

    /** EN: The asker hears the answer. / VI: Người hỏi được báo câu trả lời. */
    @EventListener
    void onAnswer(QuestionEvents.Answered event) {
        String title = auctions.lotTitleOf(event.auctionId());
        notifications.notify(event.askerId(), NotificationType.QUESTION_ANSWERED, "The seller answered",
                clip(title + ": " + event.answer()), event.auctionId(), event.at());
        mailer.send(event.askerId(), MailKind.QUESTION_ANSWERED,
                MailFacts.lot(event.auctionId(), title, null).withReason(event.answer()));
    }

    /** EN: The seller hears each review, with the stars. / VI: Người bán được báo mỗi đánh giá, kèm số sao. */
    @EventListener
    void onReview(OrderEvents.Reviewed event) {
        notifications.notify(event.sellerId(), NotificationType.NEW_REVIEW, "New review",
                auctions.lotTitleOf(event.auctionId()) + ": " + event.rating() + " of 5 stars from the buyer.",
                event.auctionId(), event.at());
        mailer.send(event.sellerId(), MailKind.NEW_REVIEW,
                MailFacts.lot(event.auctionId(), auctions.lotTitleOf(event.auctionId()), null).withRating(event.rating()));
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
