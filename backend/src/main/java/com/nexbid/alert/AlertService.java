package com.nexbid.alert;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.alert.dto.SaveSearchRequest;
import com.nexbid.auction.AuctionAuditEvent;
import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionStatus;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.mail.DigestLot;
import com.nexbid.mail.Mailer;
import com.nexbid.notification.NotificationService;
import com.nexbid.notification.NotificationType;
import com.nexbid.product.ProductCondition;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

/**
 * EN: Saved searches and followed sellers. When an admin approves a lot, everyone whose search fits it or who follows
 *     its seller gets one in-app notice; once a day {@link #sendDigests} emails each of them the new lots still open.
 * VI: Bộ lọc đã lưu và người bán đang theo dõi. Khi admin duyệt một lô, ai có bộ lọc khớp hoặc đang theo dõi người bán
 *     của lô đều nhận một thông báo trong app; mỗi ngày một lần {@link #sendDigests} gửi email cho họ các lô mới còn mở.
 */
@Service
public class AlertService {

    static final int SEARCH_LIMIT = 10;
    /** EN: What one email lists; the rest are counted. / VI: Số lô một email liệt kê; phần còn lại chỉ được đếm. */
    static final int DIGEST_SIZE = 20;
    private static final Set<AuctionStatus> OPEN = Set.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE);

    private final JdbcTemplate jdbc;
    private final AuctionService auctions;
    private final UserService users;
    private final NotificationService notifications;
    private final Mailer mailer;

    AlertService(JdbcTemplate jdbc, AuctionService auctions, UserService users, NotificationService notifications,
            Mailer mailer) {
        this.jdbc = jdbc;
        this.auctions = auctions;
        this.users = users;
        this.notifications = notifications;
        this.mailer = mailer;
    }

    // ---- saved searches / bộ lọc đã lưu ----

    @Transactional(readOnly = true)
    public List<AlertViews.SavedSearch> searchesOf(UUID userId) {
        return jdbc.query("SELECT * FROM saved_searches WHERE user_id = ? ORDER BY created_at DESC",
                (rs, n) -> search(rs), userId);
    }

    /**
     * EN: Saves the filters; saving the same filters twice returns the first one rather than a duplicate.
     * VI: Lưu bộ lọc; lưu cùng bộ lọc hai lần thì trả về cái đầu tiên chứ không tạo bản trùng.
     */
    @Transactional
    public AlertViews.SavedSearch save(UUID userId, SaveSearchRequest request) {
        String q = request.q() == null || request.q().isBlank() ? null : request.q().trim();
        List<String> categories = normalized(request.category() == null ? List.of() : request.category());
        List<String> conditions = normalized(request.condition() == null ? List.of()
                : request.condition().stream().map(ProductCondition::name).toList());
        BigDecimal min = request.minPrice();
        BigDecimal max = request.maxPrice();
        if (q == null && categories.isEmpty() && conditions.isEmpty() && min == null && max == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Choose at least one filter before saving a search");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "The lowest price is above the highest");
        }
        if (categories.stream().anyMatch(slug -> !slug.matches("[a-z0-9-]+"))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unknown category");
        }

        // EN: Locks the person's searches, so two quick saves cannot both slip under the limit.
        // VI: Khoá các bộ lọc của người này, để hai lần lưu liền nhau không cùng lọt dưới giới hạn.
        List<AlertViews.SavedSearch> mine = jdbc.query(
                "SELECT * FROM saved_searches WHERE user_id = ? ORDER BY created_at FOR UPDATE", (rs, n) -> search(rs),
                userId);
        for (AlertViews.SavedSearch existing : mine) {
            if (Objects.equals(existing.q(), q) && existing.category().equals(categories)
                    && existing.condition().equals(conditions) && sameAmount(existing.minPrice(), min)
                    && sameAmount(existing.maxPrice(), max)) {
                return existing;
            }
        }
        if (mine.size() >= SEARCH_LIMIT) {
            throw new BusinessException(ErrorCode.SAVED_SEARCH_LIMIT,
                    "You can save up to " + SEARCH_LIMIT + " searches; delete one first");
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO saved_searches (id, user_id, q, categories, conditions, min_price, max_price, created_at)
                VALUES (?, ?, ?, string_to_array(?, ','), string_to_array(?, ','), ?, ?, ?)
                """, id, userId, q, String.join(",", categories), String.join(",", conditions), min, max, utc(now));
        return new AlertViews.SavedSearch(id, q, categories, conditions, min, max, now);
    }

    @Transactional
    public void deleteSearch(UUID userId, UUID searchId) {
        if (jdbc.update("DELETE FROM saved_searches WHERE id = ? AND user_id = ?", searchId, userId) == 0) {
            throw new ResourceNotFoundException(ErrorCode.SAVED_SEARCH_NOT_FOUND, "No saved search with id " + searchId);
        }
    }

    // ---- followed sellers / người bán đang theo dõi ----

    @Transactional(readOnly = true)
    public List<AlertViews.FollowedSeller> followsOf(UUID userId) {
        record Row(UUID sellerId, Instant since) {
        }
        List<Row> rows = jdbc.query(
                "SELECT seller_id, created_at FROM seller_follows WHERE user_id = ? ORDER BY created_at DESC",
                (rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant()), userId);
        Map<UUID, String> names = users.namesOf(rows.stream().map(Row::sellerId).toList());
        return rows.stream()
                .map(row -> new AlertViews.FollowedSeller(row.sellerId(), UserService.handleOf(names.get(row.sellerId())),
                        row.since()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AlertViews.FollowState followState(UUID userId, UUID sellerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM seller_follows WHERE user_id = ? AND seller_id = ?", Integer.class, userId, sellerId);
        return new AlertViews.FollowState(sellerId, count != null && count > 0);
    }

    @Transactional
    public AlertViews.FollowState follow(UUID userId, UUID sellerId) {
        if (userId.equals(sellerId)) {
            throw new BusinessException(ErrorCode.FOLLOW_NOT_ALLOWED, "You cannot follow yourself");
        }
        users.findById(sellerId).filter(account -> account.roles().contains(RoleName.SELLER))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "No seller with id " + sellerId));
        jdbc.update("""
                INSERT INTO seller_follows (user_id, seller_id, created_at) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, userId, sellerId, utc(Instant.now()));
        return new AlertViews.FollowState(sellerId, true);
    }

    @Transactional
    public AlertViews.FollowState unfollow(UUID userId, UUID sellerId) {
        jdbc.update("DELETE FROM seller_follows WHERE user_id = ? AND seller_id = ?", userId, sellerId);
        return new AlertViews.FollowState(sellerId, false);
    }

    // ---- matching / so khớp ----

    /**
     * EN: A lot becomes public when an admin approves it; that is when it is new. Runs in the approval's transaction.
     * VI: Lô trở nên công khai khi admin duyệt; đó là lúc nó mới. Chạy trong transaction của lần duyệt.
     */
    @EventListener
    void onApproved(AuctionAuditEvent event) {
        if (event.action() == AuctionAuditEvent.Action.APPROVED) {
            match(event.auctionId(), Instant.now());
        }
    }

    /** EN: Notes the lot for everyone it fits, once each; returns how many. / VI: Ghi lô cho mọi người mà nó khớp, mỗi người một lần; trả về số người. */
    int match(UUID auctionId, Instant now) {
        AuctionSummaryView lot = auctions.participantSummariesOf(List.of(auctionId)).get(auctionId);
        if (lot == null) {
            return 0;
        }
        UUID sellerId = lot.auction().sellerId();
        String name = lot.product().name();
        String lowered = name.toLowerCase(Locale.ROOT);
        String slug = lot.category() == null ? null : lot.category().slug();

        // EN: Followers first: "a seller you follow" says more than a search. / VI: Người theo dõi trước: "người bán bạn theo dõi" nói nhiều hơn một bộ lọc.
        Map<UUID, Alert> alerts = new LinkedHashMap<>();
        for (UUID follower : jdbc.queryForList("SELECT user_id FROM seller_follows WHERE seller_id = ?", UUID.class,
                sellerId)) {
            alerts.put(follower, new Alert(follower, null, sellerId));
        }
        jdbc.query("""
                SELECT id, user_id, q FROM saved_searches
                WHERE user_id <> ?
                  AND (cardinality(categories) = 0 OR ? = ANY (categories))
                  AND (cardinality(conditions) = 0 OR ? = ANY (conditions))
                  AND (min_price IS NULL OR min_price <= ?)
                  AND (max_price IS NULL OR max_price >= ?)
                ORDER BY created_at
                """, rs -> {
            String q = rs.getString("q");
            UUID userId = rs.getObject("user_id", UUID.class);
            if (q == null || lowered.contains(q.toLowerCase(Locale.ROOT))) {
                alerts.putIfAbsent(userId, new Alert(userId, rs.getObject("id", UUID.class), null));
            }
        }, sellerId, slug, lot.product().condition(), lot.auction().currentPrice(), lot.auction().currentPrice());

        int noted = 0;
        for (Alert alert : alerts.values()) {
            int inserted = jdbc.update("""
                    INSERT INTO lot_alerts (user_id, auction_id, search_id, seller_id, created_at)
                    VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                    """, alert.userId(), auctionId, alert.searchId(), alert.sellerId(), utc(now));
            if (inserted == 0) {
                continue;
            }
            noted++;
            boolean followed = alert.sellerId() != null;
            notifications.notify(alert.userId(), NotificationType.LOT_ALERT,
                    followed ? "New lot from a seller you follow" : "New lot for your saved search",
                    clip(name), auctionId, now);
        }
        return noted;
    }

    private record Alert(UUID userId, UUID searchId, UUID sellerId) {
    }

    // ---- daily digest / email tổng hợp hằng ngày ----

    /**
     * EN: Emails each person the lots noted since their last digest that are still open, then marks every noted lot
     *     as sent, open or not, so a lot is never in two digests. Returns how many emails were queued.
     * VI: Gửi mỗi người các lô được ghi từ lần tổng hợp trước mà vẫn còn mở, rồi đánh dấu mọi lô đã ghi là đã gửi, dù
     *     còn mở hay không, để một lô không bao giờ nằm trong hai email. Trả về số email đã xếp hàng.
     */
    @Transactional
    public int sendDigests(Instant now) {
        record Pending(UUID userId, UUID auctionId, UUID searchId, UUID sellerId) {
        }
        List<Pending> pending = jdbc.query("""
                SELECT user_id, auction_id, search_id, seller_id FROM lot_alerts
                WHERE emailed_at IS NULL ORDER BY user_id, created_at
                FOR UPDATE SKIP LOCKED
                """, (rs, n) -> new Pending(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getObject(4, UUID.class)));
        if (pending.isEmpty()) {
            return 0;
        }

        Map<UUID, AuctionSummaryView> lots = auctions.participantSummariesOf(
                pending.stream().map(Pending::auctionId).distinct().toList());
        Map<UUID, String> searchWords = new java.util.HashMap<>();
        jdbc.query("SELECT id, q FROM saved_searches WHERE id = ANY (?)", rs -> {
            searchWords.put(rs.getObject(1, UUID.class), rs.getString(2));
        }, (Object) pending.stream().map(Pending::searchId).filter(Objects::nonNull).distinct().toArray(UUID[]::new));
        Map<UUID, String> sellerNames = users.namesOf(
                pending.stream().map(Pending::sellerId).filter(Objects::nonNull).distinct().toList());

        int sent = 0;
        Map<UUID, List<Pending>> byUser = pending.stream()
                .collect(Collectors.groupingBy(Pending::userId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<UUID, List<Pending>> entry : byUser.entrySet()) {
            List<DigestLot> open = new ArrayList<>();
            for (Pending row : entry.getValue()) {
                AuctionSummaryView lot = lots.get(row.auctionId());
                if (lot == null || !OPEN.contains(lot.auction().status())) {
                    continue;
                }
                String why = row.sellerId() != null ? UserService.handleOf(sellerNames.get(row.sellerId()))
                        : searchWords.get(row.searchId());
                open.add(new DigestLot(row.auctionId(), lot.product().name(), lot.auction().currentPrice(),
                        lot.auction().endTime(), why));
            }
            if (!open.isEmpty() && mailer.sendDigest(entry.getKey(), open.subList(0, Math.min(DIGEST_SIZE, open.size())),
                    open.size())) {
                sent++;
            }
        }

        jdbc.batchUpdate("UPDATE lot_alerts SET emailed_at = ? WHERE user_id = ? AND auction_id = ?",
                pending.stream().map(row -> new Object[] {utc(now), row.userId(), row.auctionId()}).toList());
        return sent;
    }

    // ---- helpers ----

    private static List<String> normalized(List<String> values) {
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty())
                .distinct().sorted().toList();
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static AlertViews.SavedSearch search(ResultSet rs) throws SQLException {
        return new AlertViews.SavedSearch(rs.getObject("id", UUID.class), rs.getString("q"),
                strings(rs.getArray("categories")), strings(rs.getArray("conditions")), rs.getBigDecimal("min_price"),
                rs.getBigDecimal("max_price"), rs.getTimestamp("created_at").toInstant());
    }

    private static List<String> strings(Array array) throws SQLException {
        return array == null ? List.of() : Arrays.stream((String[]) array.getArray()).sorted().toList();
    }

    private static String clip(String message) {
        return message.length() <= 500 ? message : message.substring(0, 499) + "…";
    }

    private static OffsetDateTime utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }
}
