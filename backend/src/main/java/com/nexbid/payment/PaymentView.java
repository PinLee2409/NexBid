package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nexbid.auction.AuctionSummaryView;

/**
 * EN: A payment as its owner sees it, with the lot behind it. Field names follow the frontend's Payment type.
 * VI: Một khoản thanh toán dưới góc nhìn chủ nhân, kèm lô đứng sau nó. Tên trường theo kiểu Payment của frontend.
 */
public record PaymentView(Details payment, AuctionSummaryView auction) {

    public record Details(
            UUID id,
            UUID auctionId,
            UUID userId,
            BigDecimal amount,
            PaymentStatus status,
            Instant expiredAt,
            Instant createdAt,
            Instant updatedAt) {
    }
}
