package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nexbid.auction.AuctionSummaryView;

/**
 * EN: A second-chance offer as the runner-up sees it, with the lot behind it.
 * VI: Một đề nghị cơ hội thứ hai dưới góc nhìn người thứ hai, kèm lô đứng sau nó.
 */
public record OfferView(Details offer, AuctionSummaryView auction) {

    public record Details(
            UUID id,
            UUID auctionId,
            BigDecimal amount,
            OfferStatus status,
            Instant expiresAt,
            Instant createdAt,
            Instant decidedAt) {
    }
}
