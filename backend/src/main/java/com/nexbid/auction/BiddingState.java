package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: A lot as bidding sees it at one instant. Carries the leader's id, so it never leaves the server.
 * VI: Một lô dưới góc nhìn của việc trả giá tại một thời điểm. Có id người dẫn nên không bao giờ rời server.
 */
public record BiddingState(
        UUID auctionId,
        UUID sellerId,
        boolean open,
        boolean ended,
        BigDecimal currentPrice,
        BigDecimal minimumIncrement,
        BigDecimal minimumNextBid,
        int bidCount,
        UUID leaderId,
        Instant endTime) {
}
