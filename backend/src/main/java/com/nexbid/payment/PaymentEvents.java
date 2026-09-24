package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
    public record Succeeded(UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount, Instant at) {
    }

    public record Expired(UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount, Instant at) {
    }
}
