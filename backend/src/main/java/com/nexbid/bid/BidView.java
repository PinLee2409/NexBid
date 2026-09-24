package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: One line of bid history. The bidder's name is masked (spec §37 shows "pin***") — who is winning is
 *     public, who exactly they are is not.
 * VI: Một dòng lịch sử trả giá. Tên người trả bị che (spec §37 hiển thị "pin***") — ai đang thắng là công
 *     khai, còn họ chính xác là ai thì không.
 */
public record BidView(
        UUID id,
        UUID auctionId,
        String bidderMask,
        boolean mine,
        BigDecimal amount,
        Instant createdAt) {
}
