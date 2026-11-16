package com.nexbid.analytics;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EN: How lots that closed in a period got through each step, by category: closed → viewed → bid on → won → paid →
 *     shipped → completed. Counted from the records themselves, not the hourly event counts, so it matches the orders
 *     page exactly. A step counts lots, not people.
 * VI: Các lô đóng trong một khoảng thời gian đi qua từng bước ra sao, theo danh mục: đóng → được xem → có trả giá →
 *     có người thắng → đã thanh toán → đã gửi hàng → hoàn tất. Đếm từ chính dữ liệu gốc, không từ số đếm sự kiện theo
 *     giờ, nên khớp đúng trang đơn hàng. Mỗi bước đếm lô, không đếm người.
 */
@Service
public class ConversionFunnel {

    static final int MAX_DAYS = 365;

    private final JdbcTemplate jdbc;

    ConversionFunnel(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** EN: One row of the funnel; {@code slug} null is the total. / VI: Một dòng của phễu; {@code slug} null là tổng. */
    public record Step(String slug, String name, int closed, int viewed, int bid, int won, int paid, int shipped,
            int completed) {
    }

    public record Funnel(Instant from, Instant to, Step total, List<Step> categories) {
    }

    @Transactional(readOnly = true)
    public Funnel lastDays(int days, Instant now) {
        int span = Math.clamp(days, 1, MAX_DAYS);
        Instant from = now.minus(java.time.Duration.ofDays(span));
        List<Step> rows = jdbc.query("""
                WITH closed AS (
                    SELECT a.id, a.bid_count, a.winner_id, c.slug, c.name
                    FROM auctions a JOIN products p ON p.id = a.product_id JOIN categories c ON c.id = p.category_id
                    WHERE a.status IN ('ENDED', 'COMPLETED', 'CANCELLED') AND a.end_time >= ? AND a.end_time < ?
                )
                SELECT slug, name,
                       COUNT(*) AS closed,
                       COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM auction_views v WHERE v.auction_id = closed.id)) AS viewed,
                       COUNT(*) FILTER (WHERE bid_count > 0) AS bid,
                       COUNT(*) FILTER (WHERE winner_id IS NOT NULL) AS won,
                       COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM payments pay
                                                      WHERE pay.auction_id = closed.id AND pay.status = 'SUCCESS')) AS paid,
                       COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM orders o WHERE o.auction_id = closed.id
                                                      AND o.status IN ('PROCESSING', 'COMPLETED', 'DISPUTED'))) AS shipped,
                       COUNT(*) FILTER (WHERE EXISTS (SELECT 1 FROM orders o WHERE o.auction_id = closed.id
                                                      AND o.status = 'COMPLETED')) AS completed
                FROM closed GROUP BY slug, name ORDER BY closed DESC, slug
                """, (rs, n) -> new Step(rs.getString("slug"), rs.getString("name"), rs.getInt("closed"),
                rs.getInt("viewed"), rs.getInt("bid"), rs.getInt("won"), rs.getInt("paid"), rs.getInt("shipped"),
                rs.getInt("completed")), from.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC));
        int[] sum = new int[7];
        for (Step row : rows) {
            sum[0] += row.closed();
            sum[1] += row.viewed();
            sum[2] += row.bid();
            sum[3] += row.won();
            sum[4] += row.paid();
            sum[5] += row.shipped();
            sum[6] += row.completed();
        }
        return new Funnel(from, now, new Step(null, null, sum[0], sum[1], sum[2], sum[3], sum[4], sum[5], sum[6]),
                new ArrayList<>(rows));
    }
}
