package com.nexbid.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.nexbid.product.ProductService;
import com.nexbid.product.ProductView;

/**
 * EN: A starting price to suggest for a product, from what lots like it sold for in the last year: lots in the same
 *     category with a similar name (trigram similarity on the accent-free name) when there are at least three, else the
 *     whole category. The suggestion is 60% of the median, rounded down to 10,000₫: low enough to draw early bids,
 *     close enough that the lot does not look cheap. Nothing to suggest without three sales.
 * VI: Giá khởi điểm gợi ý cho một sản phẩm, dựa trên giá bán của các lô tương tự trong một năm qua: lô cùng danh mục có
 *     tên gần giống (độ giống trigram trên tên không dấu) nếu có ít nhất ba lô, không thì cả danh mục. Gợi ý là 60% giá
 *     trung vị, làm tròn xuống 10.000₫: đủ thấp để hút lượt trả giá sớm, đủ gần để lô không trông rẻ. Dưới ba giao
 *     dịch thì không gợi ý.
 */
@Service
public class PriceHints {

    static final int MIN_SALES = 3;
    private static final BigDecimal SHARE = new BigDecimal("0.6");
    private static final BigDecimal ROUND = BigDecimal.valueOf(10_000);

    private final JdbcTemplate jdbc;
    private final ProductService products;

    PriceHints(JdbcTemplate jdbc, ProductService products) {
        this.jdbc = jdbc;
        this.products = products;
    }

    /** EN: {@code basis} is SIMILAR or CATEGORY; null fields when there were too few sales. / VI: {@code basis} là SIMILAR hoặc CATEGORY; các trường null khi có quá ít giao dịch. */
    public record Hint(String basis, int sales, BigDecimal low, BigDecimal median, BigDecimal high,
            BigDecimal suggestedStart) {
    }

    public Hint forProduct(UUID sellerId, UUID productId, Instant now) {
        ProductView product = products.getOwned(sellerId, productId);
        Instant since = now.minus(Duration.ofDays(365));
        List<BigDecimal> similar = jdbc.queryForList("""
                SELECT a.current_price FROM auctions a JOIN products p ON p.id = a.product_id
                WHERE a.status = 'COMPLETED' AND a.end_time >= ? AND p.category_id = ? AND p.id <> ?
                  AND similarity(fold(p.name), fold(?)) >= 0.3
                ORDER BY a.current_price
                """, BigDecimal.class, since.atOffset(ZoneOffset.UTC), product.category().id(), productId,
                product.name());
        if (similar.size() >= MIN_SALES) {
            return hint("SIMILAR", similar);
        }
        List<BigDecimal> category = jdbc.queryForList("""
                SELECT a.current_price FROM auctions a JOIN products p ON p.id = a.product_id
                WHERE a.status = 'COMPLETED' AND a.end_time >= ? AND p.category_id = ? AND p.id <> ?
                ORDER BY a.current_price
                """, BigDecimal.class, since.atOffset(ZoneOffset.UTC), product.category().id(), productId);
        if (category.size() >= MIN_SALES) {
            return hint("CATEGORY", category);
        }
        return new Hint(null, category.size(), null, null, null, null);
    }

    /** EN: Prices sorted ascending. / VI: Giá đã sắp xếp tăng dần. */
    private static Hint hint(String basis, List<BigDecimal> prices) {
        BigDecimal median = quantile(prices, 0.5);
        BigDecimal start = median.multiply(SHARE).divide(ROUND, 0, RoundingMode.DOWN).multiply(ROUND);
        return new Hint(basis, prices.size(), quantile(prices, 0.25), median, quantile(prices, 0.75),
                start.max(ROUND));
    }

    static BigDecimal quantile(List<BigDecimal> sorted, double q) {
        double position = q * (sorted.size() - 1);
        int below = (int) Math.floor(position);
        int above = (int) Math.ceil(position);
        BigDecimal low = sorted.get(below);
        BigDecimal high = sorted.get(above);
        return low.add(high.subtract(low).multiply(BigDecimal.valueOf(position - below)))
                .setScale(0, RoundingMode.HALF_UP);
    }
}
