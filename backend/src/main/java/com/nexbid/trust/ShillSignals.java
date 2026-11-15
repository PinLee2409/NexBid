package com.nexbid.trust;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EN: Seller–bidder pairs whose bidding over the last 90 days looks like the seller pushing their own prices up under
 *     another account. Each pair gets a score from plain signals an admin can check:
 *     <ul>
 *       <li>SHARED_DEVICE (50): the two accounts used the same browser;</li>
 *       <li>SHARED_NETWORK (25): the same network address, one used by five accounts at most (not an office, a
 *       school or a mobile carrier's shared address);</li>
 *       <li>MOSTLY_THIS_SELLER (20): at least 80% of the bidder's bids, on two or more of this seller's lots;</li>
 *       <li>NEVER_WINS (15): bid on three or more of this seller's lots and won none;</li>
 *       <li>RUNNER_UP (15): twice or more ended one step below the winning price on this seller's lots;</li>
 *       <li>NEW_ACCOUNT (10): the account was under 14 days old at its first bid on this seller.</li>
 *     </ul>
 *     Pairs scoring 50 or more are listed, highest first. Nothing is decided automatically.
 * VI: Các cặp người bán–người trả giá mà cách trả giá trong 90 ngày qua giống người bán tự đẩy giá lô của mình bằng tài
 *     khoản khác. Mỗi cặp có điểm từ các dấu hiệu đơn giản admin kiểm được:
 *     <ul>
 *       <li>SHARED_DEVICE (50): hai tài khoản dùng cùng một trình duyệt;</li>
 *       <li>SHARED_NETWORK (25): cùng địa chỉ mạng, mà địa chỉ đó có tối đa năm tài khoản dùng (không phải mạng công
 *       ty, trường học hay địa chỉ dùng chung của nhà mạng di động);</li>
 *       <li>MOSTLY_THIS_SELLER (20): ít nhất 80% lượt trả giá của người này, trên hai lô trở lên của người bán này;</li>
 *       <li>NEVER_WINS (15): trả giá ba lô trở lên của người bán này mà không thắng lô nào;</li>
 *       <li>RUNNER_UP (15): hai lần trở lên dừng ngay dưới giá thắng một bước giá trên lô của người bán này;</li>
 *       <li>NEW_ACCOUNT (10): tài khoản chưa tới 14 ngày tuổi khi trả giá lần đầu cho người bán này.</li>
 *     </ul>
 *     Cặp từ 50 điểm trở lên được liệt kê, cao nhất trước. Không có gì được tự động quyết định.
 */
@Service
public class ShillSignals {

    static final int THRESHOLD = 50;

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;

    ShillSignals(JdbcTemplate jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    public record Party(UUID id, String name, String email) {
    }

    public record Suspect(Party seller, Party bidder, int score, List<String> signals, long bids, long lots,
            long wins, Instant lastBid) {
    }

    /** EN: An admin cleared a pair. / VI: Admin đã xác nhận một cặp không có vấn đề. */
    public record Cleared(UUID adminId, UUID sellerId, UUID bidderId, String note) {
    }

    @Transactional(readOnly = true)
    public List<Suspect> suspects(Instant now) {
        Instant since = now.minus(Duration.ofDays(90));
        List<Suspect> found = new ArrayList<>();
        jdbc.query("""
                WITH pair AS (
                    SELECT a.seller_id, b.bidder_id, COUNT(*) AS bids, COUNT(DISTINCT b.auction_id) AS lots,
                           MIN(b.created_at) AS first_bid, MAX(b.created_at) AS last_bid
                    FROM bids b JOIN auctions a ON a.id = b.auction_id
                    WHERE b.created_at > ? AND b.bidder_id <> a.seller_id
                    GROUP BY a.seller_id, b.bidder_id
                ), total AS (
                    SELECT bidder_id, COUNT(*) AS bids FROM bids WHERE created_at > ? GROUP BY bidder_id
                ), won AS (
                    SELECT seller_id, winner_id AS bidder_id, COUNT(*) AS wins
                    FROM auctions WHERE winner_id IS NOT NULL GROUP BY seller_id, winner_id
                ), top AS (
                    SELECT auction_id, bidder_id, MAX(amount) AS amount FROM bids GROUP BY auction_id, bidder_id
                ), runner AS (
                    SELECT a.seller_id, top.bidder_id, COUNT(*) AS times
                    FROM auctions a JOIN top ON top.auction_id = a.id
                    WHERE a.winner_id IS NOT NULL AND a.winner_id <> top.bidder_id
                      AND a.current_price - top.amount <= a.minimum_increment
                    GROUP BY a.seller_id, top.bidder_id
                ), crowded AS (
                    SELECT value FROM user_origins WHERE kind = 'NETWORK'
                    GROUP BY value HAVING COUNT(DISTINCT user_id) > 5
                ), shared AS (
                    SELECT mine.user_id AS seller_id, theirs.user_id AS bidder_id,
                           bool_or(mine.kind = 'DEVICE') AS device, bool_or(mine.kind = 'NETWORK') AS network
                    FROM user_origins mine
                    JOIN user_origins theirs ON theirs.kind = mine.kind AND theirs.value = mine.value
                                            AND theirs.user_id <> mine.user_id
                    WHERE mine.kind = 'DEVICE' OR mine.value NOT IN (SELECT value FROM crowded)
                    GROUP BY mine.user_id, theirs.user_id
                )
                SELECT p.seller_id, p.bidder_id, p.bids, p.lots, p.first_bid, p.last_bid, t.bids AS all_bids,
                       COALESCE(w.wins, 0) AS wins, COALESCE(r.times, 0) AS runner_up,
                       COALESCE(sh.device, FALSE) AS shared_device, COALESCE(sh.network, FALSE) AS shared_network,
                       bidder.created_at AS joined,
                       seller.full_name AS seller_name, seller.email AS seller_email,
                       bidder.full_name AS bidder_name, bidder.email AS bidder_email
                FROM pair p
                JOIN total t ON t.bidder_id = p.bidder_id
                JOIN users seller ON seller.id = p.seller_id
                JOIN users bidder ON bidder.id = p.bidder_id
                LEFT JOIN won w ON w.seller_id = p.seller_id AND w.bidder_id = p.bidder_id
                LEFT JOIN runner r ON r.seller_id = p.seller_id AND r.bidder_id = p.bidder_id
                LEFT JOIN shared sh ON sh.seller_id = p.seller_id AND sh.bidder_id = p.bidder_id
                LEFT JOIN shill_reviews rv ON rv.seller_id = p.seller_id AND rv.bidder_id = p.bidder_id
                WHERE rv.decided_at IS NULL OR p.last_bid > rv.decided_at
                """, rs -> {
            long bids = rs.getLong("bids");
            long lots = rs.getLong("lots");
            long wins = rs.getLong("wins");
            List<String> signals = new ArrayList<>();
            int score = 0;
            if (rs.getBoolean("shared_device")) {
                signals.add("SHARED_DEVICE");
                score += 50;
            } else if (rs.getBoolean("shared_network")) {
                signals.add("SHARED_NETWORK");
                score += 25;
            }
            if (lots >= 2 && bids * 5 >= rs.getLong("all_bids") * 4) {
                signals.add("MOSTLY_THIS_SELLER");
                score += 20;
            }
            if (lots >= 3 && wins == 0) {
                signals.add("NEVER_WINS");
                score += 15;
            }
            if (rs.getLong("runner_up") >= 2) {
                signals.add("RUNNER_UP");
                score += 15;
            }
            Instant joined = rs.getTimestamp("joined").toInstant();
            if (joined.plus(Duration.ofDays(14)).isAfter(rs.getTimestamp("first_bid").toInstant())) {
                signals.add("NEW_ACCOUNT");
                score += 10;
            }
            if (score >= THRESHOLD) {
                found.add(new Suspect(
                        new Party(rs.getObject("seller_id", UUID.class), rs.getString("seller_name"),
                                rs.getString("seller_email")),
                        new Party(rs.getObject("bidder_id", UUID.class), rs.getString("bidder_name"),
                                rs.getString("bidder_email")),
                        score, signals, bids, lots, wins, rs.getTimestamp("last_bid").toInstant()));
            }
        }, since.atOffset(ZoneOffset.UTC), since.atOffset(ZoneOffset.UTC));
        found.sort(Comparator.comparingInt(Suspect::score).reversed().thenComparing(Suspect::lastBid,
                Comparator.reverseOrder()));
        return found;
    }

    /**
     * EN: Hides a pair an admin checked, until the bidder bids on that seller's lots again.
     * VI: Ẩn một cặp admin đã kiểm, tới khi người trả giá lại trả giá lô của người bán đó.
     */
    @Transactional
    public void clear(UUID adminId, UUID sellerId, UUID bidderId, String note, Instant now) {
        String trimmed = note == null || note.isBlank() ? null : note.trim();
        jdbc.update("""
                INSERT INTO shill_reviews (seller_id, bidder_id, decided_by, decided_at, note) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (seller_id, bidder_id)
                DO UPDATE SET decided_by = EXCLUDED.decided_by, decided_at = EXCLUDED.decided_at, note = EXCLUDED.note
                """, sellerId, bidderId, adminId, now.atOffset(ZoneOffset.UTC), trimmed);
        events.publishEvent(new Cleared(adminId, sellerId, bidderId, trimmed));
    }
}
