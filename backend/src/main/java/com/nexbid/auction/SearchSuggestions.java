package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.product.ProductImageService;

/**
 * EN: What the search box offers while someone types: open lots and sellers whose names contain the words, accents
 *     ignored, and a corrected spelling when nothing matches. Built on V39's fold() and V43's trigram index.
 * VI: Những gì ô tìm kiếm gợi ý khi người dùng đang gõ: lô đang mở và người bán có tên chứa các từ đó, bỏ qua dấu, và
 *     một cách viết đúng hơn khi không có gì khớp. Dựa trên hàm fold() của V39 và index trigram của V43.
 */
@Service
public class SearchSuggestions {

    static final int MIN_LENGTH = 2;
    private static final int LOTS = 6;
    private static final int SELLERS = 3;

    private final JdbcTemplate jdbc;
    private final ProductImageService images;

    SearchSuggestions(JdbcTemplate jdbc, ProductImageService images) {
        this.jdbc = jdbc;
        this.images = images;
    }

    public record LotHint(UUID auctionId, String title, BigDecimal currentPrice, String status, Instant endTime,
            String coverImageUrl) {
    }

    public record SellerHint(UUID id, String name) {
    }

    /** EN: {@code correction} is null unless nothing matched and a near spelling did. / VI: {@code correction} null trừ khi không có gì khớp mà có cách viết gần đúng. */
    public record Suggestions(String query, List<LotHint> lots, List<SellerHint> sellers, String correction) {
    }

    private record LotRow(UUID auctionId, UUID productId, String title, BigDecimal price, String status, Instant end,
            boolean exact) {
    }

    @Transactional(readOnly = true)
    public Suggestions suggest(String raw) {
        String query = raw == null ? "" : raw.strip();
        if (query.length() > 100) {
            query = query.substring(0, 100);
        }
        if (query.length() < MIN_LENGTH) {
            return new Suggestions(query, List.of(), List.of(), null);
        }
        List<LotRow> rows = jdbc.query("""
                SELECT a.id, a.product_id, p.name, a.current_price, a.status, a.end_time,
                       strpos(fold(p.name), fold(?)) > 0 AS exact
                FROM auctions a JOIN products p ON p.id = a.product_id
                WHERE a.status IN ('ACTIVE', 'SCHEDULED')
                  AND (strpos(fold(p.name), fold(?)) > 0 OR word_similarity(fold(?), fold(p.name)) >= 0.5)
                ORDER BY strpos(fold(p.name), fold(?)) = 0, a.status <> 'ACTIVE', a.end_time
                LIMIT ?
                """, (rs, n) -> new LotRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getBigDecimal(4), rs.getString(5), rs.getTimestamp(6).toInstant(), rs.getBoolean(7)),
                query, query, query, query, LOTS);
        Map<UUID, String> covers = images.coversOf(rows.stream().map(LotRow::productId).toList());
        List<LotHint> lots = rows.stream()
                .map(row -> new LotHint(row.auctionId(), row.title(), row.price(), row.status(), row.end(),
                        covers.get(row.productId())))
                .toList();
        // EN: Only accounts that list publicly, so a search never reveals who merely buys.
        // VI: Chỉ những tài khoản có lô công khai, để tìm kiếm không bao giờ để lộ ai chỉ đi mua.
        List<SellerHint> sellers = jdbc.query("""
                SELECT u.id, u.full_name FROM users u
                WHERE u.status = 'ACTIVE' AND strpos(fold(u.full_name), fold(?)) > 0
                  AND EXISTS (SELECT 1 FROM auctions a WHERE a.seller_id = u.id
                              AND a.status IN ('SCHEDULED', 'ACTIVE', 'ENDED', 'COMPLETED'))
                ORDER BY u.full_name LIMIT ?
                """, (rs, n) -> new SellerHint(rs.getObject(1, UUID.class), rs.getString(2)), query, SELLERS);
        // EN: Close matches but no exact one: the best of them is offered as the spelling. / VI: Có kết quả gần đúng nhưng không có kết quả chính xác: kết quả tốt nhất được gợi ý làm cách viết.
        String correction = rows.stream().noneMatch(LotRow::exact) && sellers.isEmpty() && !rows.isEmpty()
                ? rows.getFirst().title() : null;
        if (rows.isEmpty() && sellers.isEmpty()) {
            correction = jdbc.query("""
                    SELECT p.name FROM auctions a JOIN products p ON p.id = a.product_id
                    WHERE a.status IN ('SCHEDULED', 'ACTIVE', 'ENDED', 'COMPLETED')
                      AND word_similarity(fold(?), fold(p.name)) >= 0.3
                    ORDER BY word_similarity(fold(?), fold(p.name)) DESC, a.end_time DESC LIMIT 1
                    """, (rs, n) -> rs.getString(1), query, query).stream().findFirst().orElse(null);
        }
        return new Suggestions(query, lots, sellers, correction);
    }

    public record PastSale(UUID auctionId, String title, BigDecimal price, Instant endedAt) {
    }

    /**
     * EN: {@code basis} is SIMILAR (names alike, same category) or CATEGORY; the statistics are null below three sales.
     * VI: {@code basis} là SIMILAR (tên giống, cùng danh mục) hoặc CATEGORY; các số thống kê null khi dưới ba giao dịch.
     */
    public record PriceHistory(String basis, List<PastSale> sales, BigDecimal low, BigDecimal median,
            BigDecimal high) {
    }

    /**
     * EN: What lots like this one sold for in the past year, so a buyer knows how far to go. Sold means a winner was
     *     named and the sale did not fall through.
     * VI: Những lô giống lô này đã bán với giá bao nhiêu trong năm qua, để người mua biết nên trả tới đâu. Đã bán nghĩa
     *     là có người thắng và giao dịch không bị huỷ.
     */
    @Transactional(readOnly = true)
    public PriceHistory history(UUID auctionId, Instant now) {
        Map<String, Object> lot = jdbc.queryForMap("""
                SELECT p.name, p.category_id FROM auctions a JOIN products p ON p.id = a.product_id WHERE a.id = ?
                """, auctionId);
        String sold = """
                SELECT a.id, p.name, a.current_price, a.end_time FROM auctions a JOIN products p ON p.id = a.product_id
                WHERE a.id <> ? AND a.winner_id IS NOT NULL AND a.status IN ('ENDED', 'COMPLETED')
                  AND a.end_time >= ? AND p.category_id = ?
                """;
        Object since = now.minus(java.time.Duration.ofDays(365)).atOffset(ZoneOffset.UTC);
        List<PastSale> similar = jdbc.query(sold + " AND similarity(fold(p.name), fold(?)) >= 0.3"
                + " ORDER BY a.end_time DESC LIMIT 12", SALE_ROW, auctionId, since, lot.get("category_id"),
                lot.get("name"));
        if (similar.size() >= PriceHints.MIN_SALES) {
            return withStats("SIMILAR", similar);
        }
        List<PastSale> category = jdbc.query(sold + " ORDER BY a.end_time DESC LIMIT 12", SALE_ROW,
                auctionId, since, lot.get("category_id"));
        return category.size() >= PriceHints.MIN_SALES ? withStats("CATEGORY", category)
                : new PriceHistory(null, category, null, null, null);
    }

    private static final org.springframework.jdbc.core.RowMapper<PastSale> SALE_ROW = (rs, n) -> new PastSale(
            rs.getObject(1, UUID.class), rs.getString(2), rs.getBigDecimal(3), rs.getTimestamp(4).toInstant());

    private static PriceHistory withStats(String basis, List<PastSale> sales) {
        List<BigDecimal> prices = sales.stream().map(PastSale::price).sorted().toList();
        return new PriceHistory(basis, sales, prices.getFirst(), PriceHints.quantile(prices, 0.5), prices.getLast());
    }
}
