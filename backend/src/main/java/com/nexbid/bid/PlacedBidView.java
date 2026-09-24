package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * EN: The answer to a successful bid: the record, and where the lot stands now.
 * VI: Kết quả khi trả giá thành công: bản ghi, và tình trạng hiện tại của lô.
 */
public record PlacedBidView(
        BidView bid,
        BigDecimal currentPrice,
        int bidCount,
        BigDecimal minimumNextBid,
        Instant endTime,
        Instant serverTime) {
}
