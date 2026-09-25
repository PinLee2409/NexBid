package com.nexbid.audit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.nexbid.auction.AuctionAuditEvent;
import com.nexbid.auction.AuctionLifecycleEvent;
import com.nexbid.auction.PageView;
import com.nexbid.auth.SuccessfulLoginEvent;
import com.nexbid.bid.BidPlacedEvent;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.payment.PaymentEvents;
import com.nexbid.user.UserService;
import com.nexbid.user.UserStatusChangedEvent;

/**
 * EN: Writes an audit row in the same database transaction as the action it describes. A rolled-back
 *     action leaves no audit claim that it happened. Reads are bounded and newest first.
 * VI: Ghi dòng audit trong cùng transaction với hành động. Hành động bị rollback không để lại nhật ký
 *     nói rằng nó đã xảy ra. Đọc theo trang và mới nhất trước.
 */
@Service
public class AuditLogService {

    private static final RowMapper<AuditRow> ROW = (rs, n) -> rowOf(rs);

    private final JdbcTemplate jdbc;
    private final UserService users;

    AuditLogService(JdbcTemplate jdbc, UserService users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @EventListener
    void onLogin(SuccessfulLoginEvent event) {
        record(event.userId(), AuditAction.USER_LOGIN, "User", event.userId(), null, "SIGNED_IN");
    }

    @EventListener
    void onAuctionAction(AuctionAuditEvent event) {
        AuditAction action = switch (event.action()) {
            case CREATED -> AuditAction.AUCTION_CREATED;
            case APPROVED -> AuditAction.AUCTION_APPROVED;
            case REJECTED -> AuditAction.AUCTION_REJECTED;
        };
        record(event.actorId(), action, "Auction", event.auctionId(),
                event.before() == null ? null : event.before().name(), event.after().name());
    }

    @EventListener
    void onBid(BidPlacedEvent event) {
        record(event.bidderId(), AuditAction.BID_PLACED, "Bid", event.placed().bid().id(),
                null, event.placed().bid().amount().toPlainString());
    }

    @EventListener
    void onLifecycle(AuctionLifecycleEvent event) {
        if (AuctionLifecycleEvent.EXTENDED.equals(event.type())) {
            record(currentActor(), AuditAction.AUCTION_EXTENDED, "Auction", event.auctionId(),
                    null, event.endTime().toString());
        } else if (AuctionLifecycleEvent.ENDED.equals(event.type())) {
            record(null, AuditAction.AUCTION_ENDED, "Auction", event.auctionId(), null, "ENDED");
        }
    }

    @EventListener
    void onPayment(PaymentEvents.Succeeded event) {
        record(event.userId(), AuditAction.PAYMENT_SUCCESS, "Payment", event.paymentId(),
                null, "SUCCESS");
    }

    @EventListener
    void onUserStatus(UserStatusChangedEvent event) {
        AuditAction action = event.after() == com.nexbid.user.UserStatus.BLOCKED
                ? AuditAction.USER_BLOCKED : AuditAction.USER_UNBLOCKED;
        record(event.actorId(), action, "User", event.userId(),
                event.before().name(), event.after().name());
    }

    public PageView<AuditLogView> list(AuditLogQuery query) {
        long total = jdbc.queryForObject("SELECT count(*) FROM audit_logs", Long.class);
        List<AuditRow> rows = jdbc.query("""
                SELECT id, user_id, action, entity_type, entity_id, old_value, new_value, ip_address, created_at
                  FROM audit_logs
                 ORDER BY position DESC
                 LIMIT ? OFFSET ?
                """, ROW, query.size(), query.offset());

        Map<UUID, String> names = users.namesOf(rows.stream()
                .map(AuditRow::userId).filter(id -> id != null).distinct().toList());
        List<AuditLogView> items = rows.stream().map(row -> new AuditLogView(
                row.id(), row.userId(),
                row.userId() == null ? "system" : names.getOrDefault(row.userId(), row.userId().toString()),
                row.action(), row.entityType(), row.entityId(), row.oldValue(), row.newValue(),
                row.ipAddress() == null ? "—" : row.ipAddress(), row.createdAt())).toList();

        int pages = (int) Math.min(Integer.MAX_VALUE, (total + query.size() - 1) / query.size());
        return new PageView<>(items, query.page(), query.size(), total, pages);
    }

    private void record(
            UUID actorId, AuditAction action, String entityType, UUID entityId, String oldValue, String newValue) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Audit event must be published inside the action's transaction");
        }
        jdbc.update("""
                INSERT INTO audit_logs (id, user_id, action, entity_type, entity_id,
                                        old_value, new_value, ip_address, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), actorId, action.name(), entityType, entityId,
                oldValue, newValue, requestIp(), OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static UUID currentActor() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof CurrentUser user ? user.id() : null;
    }

    private static String requestIp() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRemoteAddr() : null;
    }

    private static AuditRow rowOf(ResultSet rs) throws SQLException {
        return new AuditRow(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                AuditAction.valueOf(rs.getString("action")), rs.getString("entity_type"),
                rs.getObject("entity_id", UUID.class), rs.getString("old_value"),
                rs.getString("new_value"), rs.getString("ip_address"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private record AuditRow(
            UUID id, UUID userId, AuditAction action, String entityType, UUID entityId,
            String oldValue, String newValue, String ipAddress, Instant createdAt) {
    }
}
