package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.modulith.events.Externalized;

/**
 * EN: A lot that closed below its reserve, and what the seller did about it. Payments open on acceptance; the
 *     notices travel through Kafka like every other lot event.
 * VI: Một lô đóng dưới giá sàn, và người bán đã làm gì với nó. Khoản thanh toán mở khi người bán nhận; thông báo đi
 *     qua Kafka như mọi sự kiện khác của lô.
 */
public final class ReserveEvents {

    private ReserveEvents() {
    }

    @Externalized("nexbid.auctions::#{auctionId()}")
    public record NotMet(UUID auctionId, UUID sellerId, UUID leaderId, BigDecimal price, Instant decideBy, Instant at) {
    }

    @Externalized("nexbid.auctions::#{auctionId()}")
    public record Accepted(UUID auctionId, UUID sellerId, UUID buyerId, BigDecimal price, Instant at) {
    }

    /** EN: `lapsed` when the seller let the window run out rather than saying no. / VI: `lapsed` khi người bán để hết hạn chứ không phải từ chối. */
    @Externalized("nexbid.auctions::#{auctionId()}")
    public record Refused(UUID auctionId, UUID sellerId, UUID leaderId, BigDecimal price, boolean lapsed, Instant at) {
    }

    /** EN: Someone bought the lot outright; kept inside the server for the audit log. / VI: Có người mua đứt lô; chỉ dùng trong server cho audit log. */
    public record BoughtNow(UUID auctionId, UUID buyerId, BigDecimal price, Instant at) {
    }
}
