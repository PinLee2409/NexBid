package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: The caller's own auto bid, and where the lot stands after it has had its say. Only ever returned to
 *     its owner — spec §14 keeps the max from everyone else.
 * VI: Auto bid của chính người gọi, và tình trạng của lô sau khi nó đã ra tay. Chỉ trả về cho chủ nhân —
 *     spec §14 giấu mức tối đa với mọi người khác.
 */
public record AutoBidView(
        UUID auctionId,
        BigDecimal maxAmount,
        boolean active,
        BigDecimal currentPrice,
        BigDecimal minimumNextBid,
        boolean leading,
        Instant updatedAt) {
}
