package com.nexbid.mail;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * EN: The email_outbox table. Plain JDBC: rows are written once, claimed in batches with SKIP LOCKED, and updated by id.
 * VI: Bảng email_outbox. Dùng JDBC thuần: dòng được ghi một lần, nhận theo lô với SKIP LOCKED, và cập nhật theo id.
 */
@Repository
class MailOutbox {

    /** EN: A row the sender works on. / VI: Một dòng mà job gửi xử lý. */
    record Pending(UUID id, String toEmail, String toName, String subject, String html, String text, int attempts,
            String unsubscribeUrl) {
    }

    private final JdbcTemplate jdbc;

    MailOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    void insert(UUID userId, String toEmail, String toName, MailKind kind, UUID auctionId,
            MailTemplates.Rendered mail, String status, Instant now) {
        insert(userId, toEmail, toName, kind, auctionId, mail, status, now, null);
    }

    void insert(UUID userId, String toEmail, String toName, MailKind kind, UUID auctionId,
            MailTemplates.Rendered mail, String status, Instant now, String unsubscribeUrl) {
        jdbc.update("""
                INSERT INTO email_outbox (id, user_id, to_email, to_name, kind, auction_id, subject, html, text,
                                          status, attempts, next_attempt_at, created_at, unsubscribe_url)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                """,
                UUID.randomUUID(), userId, toEmail, toName, kind.name(), auctionId, mail.subject(), mail.html(),
                mail.text(), status, Timestamp.from(now), Timestamp.from(now), unsubscribeUrl);
    }

    /** EN: Whether this person got this kind of email about this lot since {@code since}. / VI: Người này đã nhận loại email này về lô này kể từ {@code since} chưa. */
    boolean sentRecently(UUID userId, UUID auctionId, MailKind kind, Instant since) {
        Boolean found = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM email_outbox
                               WHERE user_id = ? AND auction_id = ? AND kind = ? AND created_at > ?)
                """, Boolean.class, userId, auctionId, kind.name(), Timestamp.from(since));
        return Boolean.TRUE.equals(found);
    }

    /**
     * EN: Due rows, locked for this transaction; another instance skips them instead of sending them twice.
     * VI: Các dòng tới hạn, khoá trong transaction này; instance khác bỏ qua chúng thay vì gửi lần hai.
     */
    List<Pending> claimDue(Instant now, int limit) {
        return jdbc.query("""
                SELECT id, to_email, to_name, subject, html, text, attempts, unsubscribe_url FROM email_outbox
                WHERE status = 'PENDING' AND next_attempt_at <= ?
                ORDER BY next_attempt_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (rs, row) -> new Pending(rs.getObject("id", UUID.class), rs.getString("to_email"),
                        rs.getString("to_name"), rs.getString("subject"), rs.getString("html"), rs.getString("text"),
                        rs.getInt("attempts"), rs.getString("unsubscribe_url")),
                Timestamp.from(now), limit);
    }

    void markSent(UUID id, Instant now) {
        jdbc.update("UPDATE email_outbox SET status = 'SENT', attempts = attempts + 1, sent_at = ?, last_error = NULL "
                + "WHERE id = ?", Timestamp.from(now), id);
    }

    /** EN: Try again later, or give up for good. / VI: Thử lại sau, hoặc bỏ hẳn. */
    void markFailed(UUID id, String error, boolean giveUp, Duration retryIn, Instant now) {
        jdbc.update("UPDATE email_outbox SET status = ?, attempts = attempts + 1, last_error = ?, next_attempt_at = ? "
                        + "WHERE id = ?",
                giveUp ? "FAILED" : "PENDING", truncate(error), Timestamp.from(now.plus(retryIn)), id);
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 500 ? error : error.substring(0, 500);
    }
}
