package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.modulith.events.Externalized;

/**
 * EN: What the payment module announces. Orders (guide §33) and notifications listen; this module does
 *     not know they exist. Each listener chooses whether it runs inside the transaction or after commit.
 * VI: Những gì module thanh toán loan báo. Đơn hàng (guide §33) và thông báo lắng nghe; module này không biết
 *     chúng tồn tại. Mỗi listener tự chọn chạy trong transaction hay sau commit.
 */
public final class PaymentEvents {

    private PaymentEvents() {
    }

    /**
     * EN: A payment was opened for a winner. Delivered inside the closing transaction, so whatever listens
     *     (the order) is created in the same step as the payment, or not at all.
     * VI: Một khoản thanh toán vừa được mở cho người thắng. Giao ngay trong transaction đóng phiên, nên thứ
     *     lắng nghe (đơn hàng) được tạo cùng một bước với khoản thanh toán, hoặc không được tạo gì cả.
     */
    public record Opened(UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount) {
    }

    /** EN: Spec §19 names it PaymentSucceededEvent. / VI: Spec §19 gọi nó là PaymentSucceededEvent. */
    @Externalized("nexbid.payments::#{auctionId()}")
    public record Succeeded(UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount, Instant at,
            BigDecimal shippingFee, BigDecimal sellerDiscount) {

        public Succeeded(UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount, Instant at,
                BigDecimal shippingFee) {
            this(paymentId, auctionId, userId, amount, at, shippingFee, BigDecimal.ZERO);
        }

        public BigDecimal total() {
            BigDecimal goods = sellerDiscount == null ? amount : amount.subtract(sellerDiscount);
            return shippingFee == null ? goods : goods.add(shippingFee);
        }
    }

    @Externalized("nexbid.payments::#{auctionId()}")
    public record Expired(UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount, Instant at) {
    }

    /**
     * EN: An admin transferred a payment back to the buyer. {@code account} is masked ("VCB ••••1234").
     * VI: Admin đã chuyển trả một khoản thanh toán cho người mua. {@code account} đã che bớt ("VCB ••••1234").
     */
    public record Refunded(UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount, String reference,
            String account, UUID actorId, Instant at) {
    }

    /**
     * EN: VNPay took money no payment could use (LATE or DUPLICATE); it waits for an admin to transfer it back.
     * VI: VNPay đã thu tiền mà không khoản thanh toán nào dùng được (LATE hoặc DUPLICATE); chờ admin chuyển trả lại.
     */
    public record StrayCharged(UUID strayId, UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount,
            String reason, Instant at) {
    }

    /** EN: An admin transferred a stray charge back. / VI: Admin đã chuyển trả lại một khoản thu thừa. */
    public record StrayRefunded(UUID strayId, UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount,
            String reference, String account, UUID actorId, Instant at) {
    }
}
