package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: What the payment module announces, delivered after commit. Orders (guide §33) and notifications
 *     listen; this module does not know they exist.
 * VI: Những gì module thanh toán loan báo, giao đi sau commit. Đơn hàng (guide §33) và thông báo lắng nghe;
 *     module này không biết chúng tồn tại.
 */
public final class PaymentEvents {

    private PaymentEvents() {
    }

    /** EN: Spec §19 names it PaymentSucceededEvent. / VI: Spec §19 gọi nó là PaymentSucceededEvent. */
    public record Succeeded(UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount, Instant at) {
    }

    public record Expired(UUID paymentId, UUID auctionId, UUID userId, BigDecimal amount, Instant at) {
    }
}
