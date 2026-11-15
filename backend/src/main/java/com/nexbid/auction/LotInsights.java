package com.nexbid.auction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EN: What the lot pages and the seller learn from traffic: lots like the one being viewed, how many people looked at a
 *     lot, and each seller's numbers per lot.
 * VI: Những gì trang lô và người bán biết được từ lượt truy cập: các lô giống lô đang xem, bao nhiêu người đã xem một
 *     lô, và số liệu của từng lô cho người bán.
 */
@Service
public class LotInsights {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int SIMILAR = 8;

    private final JdbcTemplate jdbc;
    private final AuctionService auctions;

    LotInsights(JdbcTemplate jdbc, AuctionService auctions) {
        this.jdbc = jdbc;
        this.auctions = auctions;
    }

    /**
     * EN: Up to eight open or upcoming lots in the same category, closing soonest first, other sellers' before the same
     *     seller's. A hidden lot answers 404 like its page.
     * VI: Tối đa tám lô đang mở hoặc sắp mở cùng danh mục, lô sắp đóng trước, lô của người bán khác trước lô của cùng
     *     người bán. Lô bị ẩn trả 404 như trang của nó.
     */
    @Transactional(readOnly = true)
    public List<AuctionSummaryView> similarTo(UUID auctionId) {
        auctions.requirePubliclyVisible(auctionId);
        List<UUID> ids = jdbc.queryForList("""
                SELECT other.id
                FROM auctions lot
                JOIN products product ON product.id = lot.product_id
                JOIN products kin ON kin.category_id = product.category_id
                JOIN auctions other ON other.product_id = kin.id
                WHERE lot.id = ?
                  AND other.id <> lot.id
                  AND other.status IN ('ACTIVE', 'SCHEDULED')
                ORDER BY (other.seller_id = lot.seller_id), other.status, other.end_time
                LIMIT ?
                """, UUID.class, auctionId, SIMILAR);
        return auctions.publicSummariesOf(ids);
    }

    /**
     * EN: Counts a visit, once per viewer per day. The seller looking at their own lot is not counted.
     * VI: Đếm một lượt xem, mỗi người xem một lần mỗi ngày. Người bán xem lô của chính mình không được đếm.
     */
    @Transactional
    public void recordView(UUID auctionId, UUID viewerId, String address, Instant at) {
        if (viewerId != null && viewerId.equals(auctions.sellerOf(auctionId))) {
            return;
        }
        String key = viewerId != null ? "u:" + viewerId : "a:" + hash(address == null ? "" : address);
        jdbc.update("""
                INSERT INTO auction_views (auction_id, viewer_key, day) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, auctionId, key, LocalDate.ofInstant(at, VIETNAM));
    }

    /** EN: One of the seller's lots and how it is doing. / VI: Một lô của người bán và tình hình của nó. */
    public record LotStats(UUID auctionId, String name, AuctionStatus status, BigDecimal currentPrice, Instant endTime,
            long views, long watchers, long bidders, long bids) {
    }

    /**
     * EN: The seller's figures. {@code sellThrough} is the share of closed lots whose winner paid; null until a lot
     *     has closed.
     * VI: Số liệu của người bán. {@code sellThrough} là tỉ lệ lô đã đóng mà người thắng đã trả tiền; null khi chưa lô nào
     *     đóng.
     */
    public record SellerStats(long views, long watchers, long bids, long closed, long sold, Double sellThrough,
            List<LotStats> lots) {
    }

    @Transactional(readOnly = true)
    public SellerStats forSeller(UUID sellerId) {
        List<LotStats> lots = jdbc.query("""
                SELECT a.id, p.name, a.status, a.current_price, a.end_time,
                       (SELECT COUNT(*) FROM auction_views v WHERE v.auction_id = a.id) AS views,
                       (SELECT COUNT(*) FROM watchlists w WHERE w.auction_id = a.id) AS watchers,
                       (SELECT COUNT(DISTINCT b.bidder_id) FROM bids b WHERE b.auction_id = a.id) AS bidders,
                       (SELECT COUNT(*) FROM bids b WHERE b.auction_id = a.id) AS bids
                FROM auctions a
                JOIN products p ON p.id = a.product_id
                WHERE a.seller_id = ? AND a.status NOT IN ('DRAFT', 'REJECTED')
                ORDER BY a.end_time DESC
                LIMIT 200
                """, (rs, n) -> new LotStats(rs.getObject("id", UUID.class), rs.getString("name"),
                AuctionStatus.valueOf(rs.getString("status")), rs.getBigDecimal("current_price"),
                rs.getTimestamp("end_time").toInstant(), rs.getLong("views"), rs.getLong("watchers"),
                rs.getLong("bidders"), rs.getLong("bids")), sellerId);

        long closed = jdbc.queryForObject("""
                SELECT COUNT(*) FROM auctions WHERE seller_id = ? AND status IN ('ENDED', 'COMPLETED', 'CANCELLED')
                """, Long.class, sellerId);
        // EN: A lot is COMPLETED once its winner has paid. / VI: Lô thành COMPLETED khi người thắng đã trả tiền.
        long sold = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auctions WHERE seller_id = ? AND status = 'COMPLETED'", Long.class, sellerId);
        return new SellerStats(
                lots.stream().mapToLong(LotStats::views).sum(),
                lots.stream().mapToLong(LotStats::watchers).sum(),
                lots.stream().mapToLong(LotStats::bids).sum(),
                closed, sold, closed == 0 ? null : (double) sold / closed, lots);
    }

    private static String hash(String address) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
