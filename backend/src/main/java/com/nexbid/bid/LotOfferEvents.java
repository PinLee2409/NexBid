package com.nexbid.bid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: What happens to an offer, for notices and emails.
 * VI: Những gì xảy ra với một đề nghị, cho thông báo và email.
 */
public final class LotOfferEvents {

    private LotOfferEvents() {
    }

    public record Made(UUID offerId, UUID auctionId, UUID sellerId, UUID buyerId, BigDecimal amount, Instant at) {
    }

    /**
     * EN: {@code outcome} is ACCEPTED, DECLINED (by the seller), COUNTERED or COUNTER_DECLINED (by the buyer);
     *     {@code price} is the sale price, the counter, or the offer.
     * VI: {@code outcome} là ACCEPTED, DECLINED (người bán từ chối), COUNTERED hoặc COUNTER_DECLINED (người mua từ chối);
     *     {@code price} là giá bán, giá trả lại, hoặc giá đề nghị.
     */
    public record Answered(UUID offerId, UUID auctionId, UUID sellerId, UUID buyerId, String outcome, BigDecimal price,
            Instant at) {
    }
}
