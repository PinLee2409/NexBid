package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.modulith.events.Externalized;

/**
 * EN: The life of a second-chance offer (spec §17). Notifications hear them through Kafka; the audit log in-process.
 * VI: Vòng đời một đề nghị cơ hội thứ hai (spec §17). Thông báo nghe qua Kafka; audit log nghe ngay trong server.
 */
public final class SecondChanceEvents {

    private SecondChanceEvents() {
    }

    @Externalized("nexbid.payments::#{auctionId()}")
    public record Offered(
            UUID offerId, UUID auctionId, UUID sellerId, UUID buyerId, BigDecimal amount, Instant expiresAt, Instant at) {
    }

    @Externalized("nexbid.payments::#{auctionId()}")
    public record Accepted(UUID offerId, UUID auctionId, UUID sellerId, UUID buyerId, BigDecimal amount, Instant at) {
    }

    /** EN: `lapsed` when the 24 hours ran out rather than the buyer saying no. / VI: `lapsed` khi hết 24 giờ chứ không phải người mua từ chối. */
    @Externalized("nexbid.payments::#{auctionId()}")
    public record Declined(UUID offerId, UUID auctionId, UUID sellerId, UUID buyerId, boolean lapsed, Instant at) {
    }
}
