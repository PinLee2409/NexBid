package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: A lot that closed unsold was listed again by itself, {@code newStart} instead of {@code oldStart}.
 * VI: Một lô đóng mà không bán được đã tự được đăng lại, khởi điểm {@code newStart} thay vì {@code oldStart}.
 */
public record AuctionRelistedEvent(UUID oldAuctionId, UUID newAuctionId, UUID sellerId, BigDecimal oldStart,
        BigDecimal newStart, Instant opensAt) {
}
