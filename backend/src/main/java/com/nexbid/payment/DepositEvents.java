package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: A held deposit reached its end: REFUNDED (an admin's transfer, {@code actorId}) or FORFEITED.
 * VI: Một khoản cọc đang giữ đã kết thúc: REFUNDED (lần chuyển khoản của admin, {@code actorId}) hoặc FORFEITED.
 */
public final class DepositEvents {

    private DepositEvents() {
    }

    public record Settled(UUID depositId, UUID auctionId, UUID userId, BigDecimal amount, String outcome, UUID actorId,
            Instant at) {
    }
}
