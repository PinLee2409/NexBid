package com.nexbid.auction;

import org.springframework.stereotype.Component;

import com.nexbid.auction.repository.AuctionRepository;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * EN: active_auctions (spec §34): lots open for bidding, counted in the database at each scrape.
 * VI: active_auctions (spec §34): số lô đang mở trả giá, đếm trong database mỗi lần bị lấy số liệu.
 */
@Component
class AuctionMetrics implements MeterBinder {

    private final AuctionRepository auctions;

    AuctionMetrics(AuctionRepository auctions) {
        this.auctions = auctions;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("active.auctions", auctions, repository -> repository.countByStatus(AuctionStatus.ACTIVE))
                .description("Lots open for bidding right now")
                .register(registry);
    }
}
