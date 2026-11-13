package com.nexbid.payout;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: Money that left the platform for a seller. / VI: Tiền đã rời sàn để tới một người bán.
 */
public final class PayoutEvents {

    private PayoutEvents() {
    }

    /** EN: An admin recorded a bank transfer to the seller. / VI: Admin đã ghi nhận một lần chuyển khoản cho người bán. */
    public record Paid(UUID payoutId, UUID sellerId, BigDecimal amount, String reference, UUID actorId, Instant at) {
    }
}
