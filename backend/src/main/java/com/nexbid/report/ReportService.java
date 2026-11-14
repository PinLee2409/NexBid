package com.nexbid.report;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionStatus;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.user.UserService;

/**
 * EN: Reports about lots. A signed-in person other than the seller reports a scheduled or live lot, once at a time.
 *     An admin works the queue lot by lot: dismiss a report, or take the lot down, which closes every open report on
 *     it. Reporters hear the outcome; the seller hears only of a takedown (from the auction module).
 * VI: Báo cáo về lô. Người đã đăng nhập, trừ người bán, báo cáo một lô đang chờ mở hoặc đang mở, mỗi lần một báo cáo.
 *     Admin xử lý hàng chờ theo từng lô: bỏ qua báo cáo, hoặc gỡ lô, việc này đóng mọi báo cáo đang mở của lô. Người báo
 *     cáo được báo kết quả; người bán chỉ được báo khi lô bị gỡ (do module auction báo).
 */
@Service
public class ReportService {

    public enum Reason { COUNTERFEIT, NOT_AS_DESCRIBED, PROHIBITED, OTHER }

    public enum Status { OPEN, DISMISSED, REMOVED }

    /** EN: Waiting reports beyond this per person mean spam. / VI: Quá số báo cáo đang chờ này mỗi người là spam. */
    static final int OPEN_LIMIT = 10;
    private static final Set<AuctionStatus> REPORTABLE = Set.of(AuctionStatus.SCHEDULED, AuctionStatus.ACTIVE);

    public record ReportView(UUID id, UUID auctionId, String reporter, Reason reason, String details, Status status,
            String note, Instant createdAt, Instant resolvedAt, AuctionSummaryView auction) {
    }

    private record Row(UUID id, UUID auctionId, UUID reporterId, Reason reason, String details, Status status,
            String note, Instant createdAt, Instant resolvedAt) {
    }

    private final JdbcTemplate jdbc;
    private final AuctionService auctions;
    private final UserService users;
    private final ApplicationEventPublisher events;

    ReportService(JdbcTemplate jdbc, AuctionService auctions, UserService users, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.auctions = auctions;
        this.users = users;
        this.events = events;
    }

    @Transactional
    public ReportView report(UUID reporterId, UUID auctionId, Reason reason, String details) {
        auctions.requirePubliclyVisible(auctionId);
        AuctionSummaryView lot = auctions.participantSummariesOf(List.of(auctionId)).get(auctionId);
        if (!REPORTABLE.contains(lot.auction().status())) {
            throw new BusinessException(ErrorCode.REPORT_NOT_ALLOWED, "Only a scheduled or live lot can be reported");
        }
        if (lot.auction().sellerId().equals(reporterId)) {
            throw new BusinessException(ErrorCode.REPORT_NOT_ALLOWED, "You cannot report your own lot");
        }
        Integer open = jdbc.queryForObject("SELECT COUNT(*) FROM lot_reports WHERE reporter_id = ? AND status = 'OPEN'",
                Integer.class, reporterId);
        if (open != null && open >= OPEN_LIMIT) {
            throw new BusinessException(ErrorCode.REPORT_LIMIT,
                    "You already have " + OPEN_LIMIT + " reports waiting for review");
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbc.update("""
                    INSERT INTO lot_reports (id, auction_id, reporter_id, reason, details, status, created_at)
                    VALUES (?, ?, ?, ?, ?, 'OPEN', ?)
                    """, id, auctionId, reporterId, reason.name(), details.trim(), utc(now));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.REPORT_DUPLICATE, "You have already reported this lot");
        }
        return view(new Row(id, auctionId, reporterId, reason, details.trim(), Status.OPEN, null, now, null),
                users.namesOf(List.of(reporterId)), Map.of(auctionId, lot));
    }

    /** EN: Open reports oldest first; decided ones newest first. / VI: Báo cáo đang mở cũ nhất trước; đã xử lý mới nhất trước. */
    @Transactional(readOnly = true)
    public List<ReportView> list(Status status) {
        String order = status == Status.OPEN ? "created_at" : "resolved_at DESC";
        List<Row> rows = jdbc.query("SELECT * FROM lot_reports WHERE status = ? ORDER BY " + order + " LIMIT 200",
                (rs, n) -> row(rs), status.name());
        Map<UUID, String> names = users.namesOf(rows.stream().map(Row::reporterId).distinct().toList());
        Map<UUID, AuctionSummaryView> lots = auctions.participantSummariesOf(
                rows.stream().map(Row::auctionId).distinct().toList());
        return rows.stream().map(row -> view(row, names, lots)).toList();
    }

    /** EN: The report does not hold up; the lot stays. / VI: Báo cáo không có cơ sở; lô vẫn giữ. */
    @Transactional
    public ReportView dismiss(UUID adminId, UUID reportId, String note) {
        Row report = lockOpen(reportId);
        Instant now = Instant.now();
        String text = blankToNull(note);
        jdbc.update("UPDATE lot_reports SET status = 'DISMISSED', note = ?, resolved_at = ?, resolved_by = ? WHERE id = ?",
                text, utc(now), adminId, reportId);
        events.publishEvent(new ReportEvents.Resolved(reportId, report.auctionId(), report.reporterId(),
                Status.DISMISSED.name(), text, adminId, now));
        return view(new Row(report.id(), report.auctionId(), report.reporterId(), report.reason(), report.details(),
                Status.DISMISSED, text, report.createdAt(), now), users.namesOf(List.of(report.reporterId())),
                auctions.participantSummariesOf(List.of(report.auctionId())));
    }

    /**
     * EN: The report holds: the lot is taken down with the admin's reason (shown to the seller), and every open report
     *     on it closes as REMOVED. Returns how many reports were closed.
     * VI: Báo cáo đúng: lô bị gỡ kèm lý do của admin (hiện cho người bán), và mọi báo cáo đang mở của lô đóng với trạng
     *     thái REMOVED. Trả về số báo cáo đã đóng.
     */
    @Transactional
    public int takeDown(UUID adminId, UUID reportId, String reason) {
        Row report = lockOpen(reportId);
        auctions.remove(report.auctionId(), adminId, reason);

        Instant now = Instant.now();
        String text = reason.trim();
        List<Row> open = jdbc.query("SELECT * FROM lot_reports WHERE auction_id = ? AND status = 'OPEN' FOR UPDATE",
                (rs, n) -> row(rs), report.auctionId());
        jdbc.update("""
                UPDATE lot_reports SET status = 'REMOVED', note = ?, resolved_at = ?, resolved_by = ?
                WHERE auction_id = ? AND status = 'OPEN'
                """, text, utc(now), adminId, report.auctionId());
        for (Row each : open) {
            events.publishEvent(new ReportEvents.Resolved(each.id(), each.auctionId(), each.reporterId(),
                    Status.REMOVED.name(), text, adminId, now));
        }
        return open.size();
    }

    private Row lockOpen(UUID reportId) {
        Row report = jdbc.query("SELECT * FROM lot_reports WHERE id = ? FOR UPDATE", (rs, n) -> row(rs), reportId)
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REPORT_NOT_FOUND,
                        "No report with id " + reportId));
        if (report.status() != Status.OPEN) {
            throw new BusinessException(ErrorCode.REPORT_CLOSED, "This report has already been decided");
        }
        return report;
    }

    private static ReportView view(Row row, Map<UUID, String> names, Map<UUID, AuctionSummaryView> lots) {
        return new ReportView(row.id(), row.auctionId(), UserService.handleOf(names.get(row.reporterId())),
                row.reason(), row.details(), row.status(), row.note(), row.createdAt(), row.resolvedAt(),
                lots.get(row.auctionId()));
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("auction_id", UUID.class),
                rs.getObject("reporter_id", UUID.class), Reason.valueOf(rs.getString("reason")), rs.getString("details"),
                Status.valueOf(rs.getString("status")), rs.getString("note"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("resolved_at")));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant instant(Timestamp value) {
        return Optional.ofNullable(value).map(Timestamp::toInstant).orElse(null);
    }

    private static OffsetDateTime utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }
}
