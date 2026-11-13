package com.nexbid.order;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * EN: Buyers' complaints about orders (table order_disputes). One open dispute per order, enforced by a unique index.
 * VI: Khiếu nại của người mua về đơn hàng (bảng order_disputes). Mỗi đơn một khiếu nại đang mở, do unique index đảm bảo.
 */
@Component
class DisputeBook {

    /** EN: Why the buyer complains. / VI: Người mua khiếu nại vì sao. */
    enum Reason { NOT_RECEIVED, NOT_AS_DESCRIBED, DAMAGED, OTHER }

    /** EN: Open until an admin refunds or rejects it. / VI: Mở cho tới khi admin hoàn tiền hoặc bác. */
    enum Status { OPEN, REFUNDED, REJECTED }

    record Row(UUID id, UUID orderId, UUID buyerId, Reason reason, String details, OrderStatus priorStatus,
            Status status, String resolutionNote, Instant createdAt, Instant resolvedAt) {

        OrderView.Dispute view() {
            return new OrderView.Dispute(id, reason.name(), details, status.name(), resolutionNote, createdAt,
                    resolvedAt);
        }
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    DisputeBook(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
    }

    UUID open(UUID orderId, UUID buyerId, Reason reason, String details, OrderStatus prior, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO order_disputes (id, order_id, buyer_id, reason, details, prior_status, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'OPEN', ?)
                """, id, orderId, buyerId, reason.name(), details, prior.name(), utc(at));
        return id;
    }

    /** EN: Locked, so two admins cannot decide the same dispute twice. / VI: Khoá lại, để hai admin không quyết định cùng một khiếu nại hai lần. */
    Optional<Row> lockById(UUID id) {
        return jdbc.query("SELECT * FROM order_disputes WHERE id = ? FOR UPDATE", this::row, id).stream().findFirst();
    }

    Optional<Row> openFor(UUID orderId) {
        return jdbc.query("SELECT * FROM order_disputes WHERE order_id = ? AND status = 'OPEN' FOR UPDATE",
                this::row, orderId).stream().findFirst();
    }

    void resolve(UUID id, Status status, String note, UUID adminId, Instant at) {
        jdbc.update("""
                UPDATE order_disputes SET status = ?, resolution_note = ?, resolved_by = ?, resolved_at = ?
                WHERE id = ? AND status = 'OPEN'
                """, status.name(), note, adminId, utc(at), id);
    }

    /** EN: The newest dispute of each order, open or not. / VI: Khiếu nại mới nhất của mỗi đơn, đang mở hay không. */
    Map<UUID, Row> latestFor(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        List<Row> rows = named.query("""
                SELECT DISTINCT ON (order_id) * FROM order_disputes
                WHERE order_id IN (:ids) ORDER BY order_id, created_at DESC
                """, new MapSqlParameterSource("ids", orderIds), this::row);
        return rows.stream().collect(Collectors.toMap(Row::orderId, Function.identity()));
    }

    /** EN: Disputes in a status, oldest first, so the longest-waiting buyer is seen first. / VI: Khiếu nại theo trạng thái, cũ nhất trước, để người chờ lâu nhất được xem trước. */
    List<Row> byStatus(Status status, int limit) {
        return jdbc.query("SELECT * FROM order_disputes WHERE status = ? ORDER BY created_at "
                + (status == Status.OPEN ? "ASC" : "DESC") + " LIMIT ?", this::row, status.name(), limit);
    }

    private Row row(ResultSet rs, int n) throws SQLException {
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getObject("buyer_id", UUID.class),
                Reason.valueOf(rs.getString("reason")),
                rs.getString("details"),
                OrderStatus.valueOf(rs.getString("prior_status")),
                Status.valueOf(rs.getString("status")),
                rs.getString("resolution_note"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("resolved_at")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }
}
