package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * EN: The answer to a successful bid: the record, and where the lot stands once any auto bids have
 *     answered it. {@code leading} tells the bidder straight away if an auto bid has already taken over.
 * VI: Kết quả khi trả giá thành công: bản ghi, và tình trạng của lô sau khi các auto bid đã đáp trả.
 *     {@code leading} báo ngay cho người trả giá nếu một auto bid đã vượt lên trước.
 */
public record PlacedBidView(
        BidView bid,
        BigDecimal currentPrice,
        int bidCount,
        BigDecimal minimumNextBid,
        Instant endTime,
        Instant serverTime,
        boolean leading) {
}
