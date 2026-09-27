package com.nexbid.analytics;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * EN: One UTC hour on the marketplace. "Sold" means the lot closed with a winner; revenue is what was paid.
 * VI: Một giờ UTC trên sàn. "Bán được" là lô đóng có người thắng; doanh thu là số tiền đã được thanh toán.
 */
public record AnalyticsHour(
        Instant hour, int bids, int auctionsEnded, int auctionsSold, int payments, BigDecimal revenue) {

    static AnalyticsHour empty(Instant hour) {
        return new AnalyticsHour(hour, 0, 0, 0, 0, BigDecimal.ZERO);
    }
}
