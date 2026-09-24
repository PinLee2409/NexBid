package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: An auction as the API returns it.
 * VI: Một phiên đấu giá dưới dạng API trả về.
 */
public record AuctionView(
        UUID id,
        UUID productId,
        UUID sellerId,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal minimumIncrement,
        Instant startTime,
        Instant endTime,
        AuctionStatus status,
        AntiSniping antiSniping,
        int extensionCount,
        int bidCount,
        UUID winnerId,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt) {

    /** EN: Grouped, because these three only mean anything together. / VI: Gom lại, vì ba giá trị này chỉ có nghĩa khi đi cùng nhau. */
    public record AntiSniping(boolean enabled, int windowSeconds, int extensionSeconds) {
    }
}
