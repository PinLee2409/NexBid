package com.nexbid.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * EN: The hourly totals the Analytics Consumer builds (spec §19), and the report the admin overview reads.
 * VI: Số liệu tổng theo giờ mà Analytics Consumer dựng (spec §19), và bản báo cáo trang tổng quan admin đọc.
 */
@Service
public class AnalyticsService {

    /** EN: A week at most, so a request cannot ask for years of rows. / VI: Tối đa một tuần, để request không đòi được hàng năm trời dữ liệu. */
    static final int MAX_HOURS = 168;

    private static final RowMapper<AnalyticsHour> ROW = (rs, rowNum) -> new AnalyticsHour(
            rs.getObject("hour", OffsetDateTime.class).toInstant(),
            rs.getInt("bids"), rs.getInt("auctions_ended"), rs.getInt("auctions_sold"), rs.getInt("payments"),
            rs.getBigDecimal("revenue"));

    private final JdbcTemplate jdbc;

    AnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * EN: Adds to the totals of the UTC hour `at` falls in, creating the row the first time.
     * VI: Cộng vào số liệu của giờ UTC chứa thời điểm `at`, tạo dòng mới ở lần đầu.
     */
    void add(Instant at, int bids, int auctionsEnded, int auctionsSold, int payments, BigDecimal revenue) {
        jdbc.update("""
                INSERT INTO analytics_hourly (hour, bids, auctions_ended, auctions_sold, payments, revenue)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (hour) DO UPDATE SET
                    bids           = analytics_hourly.bids + EXCLUDED.bids,
                    auctions_ended = analytics_hourly.auctions_ended + EXCLUDED.auctions_ended,
                    auctions_sold  = analytics_hourly.auctions_sold + EXCLUDED.auctions_sold,
                    payments       = analytics_hourly.payments + EXCLUDED.payments,
                    revenue        = analytics_hourly.revenue + EXCLUDED.revenue
                """, OffsetDateTime.ofInstant(at.truncatedTo(ChronoUnit.HOURS), ZoneOffset.UTC),
                bids, auctionsEnded, auctionsSold, payments, revenue);
    }

    /**
     * EN: The last `hours` hours up to and including the current one, quiet hours included as zeros.
     * VI: `hours` giờ gần nhất tính cả giờ hiện tại, giờ không có gì vẫn được đưa vào với số 0.
     */
    public AnalyticsReport lastHours(int hours, Instant now) {
        int span = Math.clamp(hours, 1, MAX_HOURS);
        Instant last = now.truncatedTo(ChronoUnit.HOURS);
        Instant first = last.minus(span - 1L, ChronoUnit.HOURS);

        Map<Instant, AnalyticsHour> stored = jdbc.query(
                        "SELECT * FROM analytics_hourly WHERE hour BETWEEN ? AND ?", ROW,
                        OffsetDateTime.ofInstant(first, ZoneOffset.UTC), OffsetDateTime.ofInstant(last, ZoneOffset.UTC))
                .stream()
                .collect(Collectors.toMap(AnalyticsHour::hour, Function.identity()));

        List<AnalyticsHour> filled = IntStream.range(0, span)
                .mapToObj(offset -> first.plus(offset, ChronoUnit.HOURS))
                .map(hour -> stored.getOrDefault(hour, AnalyticsHour.empty(hour)))
                .toList();

        int ended = filled.stream().mapToInt(AnalyticsHour::auctionsEnded).sum();
        int sold = filled.stream().mapToInt(AnalyticsHour::auctionsSold).sum();
        return new AnalyticsReport(filled, new AnalyticsReport.Totals(
                filled.stream().mapToInt(AnalyticsHour::bids).sum(),
                ended,
                sold,
                filled.stream().mapToInt(AnalyticsHour::payments).sum(),
                filled.stream().map(AnalyticsHour::revenue).reduce(BigDecimal.ZERO, BigDecimal::add),
                ended == 0 ? null : (double) sold / ended));
    }
}
