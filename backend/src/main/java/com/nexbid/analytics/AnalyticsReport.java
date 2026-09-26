package com.nexbid.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * EN: The last hours, oldest first with the empty ones filled in, and their totals.
 * VI: Các giờ gần đây, cũ nhất trước với giờ trống được điền vào, cùng tổng của chúng.
 */
public record AnalyticsReport(List<AnalyticsHour> hours, Totals totals) {

    /** EN: sellThrough is sold ÷ ended, null when nothing ended. / VI: sellThrough là bán được ÷ đã đóng, null khi chưa lô nào đóng. */
    public record Totals(
            int bids, int auctionsEnded, int auctionsSold, int payments, BigDecimal revenue, Double sellThrough) {
    }
}
