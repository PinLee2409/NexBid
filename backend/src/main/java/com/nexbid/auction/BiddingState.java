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
        Instant endTime,
        // EN: Null without a reserve. Server-side only, like the leader. / VI: Null nếu không có giá sàn. Chỉ dùng trong server, như người dẫn.
        BigDecimal reservePrice) {

    /** EN: Null without a reserve. / VI: Null nếu không có giá sàn. */
    public Boolean reserveMet() {
        return reservePrice == null ? null : bidCount > 0 && currentPrice.compareTo(reservePrice) >= 0;
    }
}
