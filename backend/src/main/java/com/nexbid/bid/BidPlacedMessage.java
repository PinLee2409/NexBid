package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: What every browser watching a lot receives when a bid lands (guide §23). It carries the new minimum
 *     too — spec §46 says the frontend does not decide prices, and working it out client-side would be
 *     exactly that.
 * VI: Thứ mà mọi trình duyệt đang xem một lô nhận được khi có lượt trả giá (guide §23). Kèm luôn mức tối
 *     thiểu mới — spec §46 nói frontend không quyết định giá, mà tự tính ở client thì đúng là như vậy.
 */
public record BidPlacedMessage(
        String type,
        UUID auctionId,
        BigDecimal currentPrice,
        int bidCount,
        BigDecimal minimumNextBid,
        String bidder,
        Instant createdAt) {

    public static final String TYPE = "BID_PLACED";

    public static BidPlacedMessage of(PlacedBidView placed) {
        return new BidPlacedMessage(
                TYPE,
                placed.bid().auctionId(),
                placed.currentPrice(),
                placed.bidCount(),
                placed.minimumNextBid(),
                placed.bid().bidderMask(),
                placed.bid().createdAt());
    }
}
