package com.nexbid.mail;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.common.response.ApiResponse;

/**
 * EN: The email outbox as an admin sees it: how many emails sit in each state, the latest ones in a state, and a way to
 *     send a FAILED one again (after fixing a sender address or a Brevo outage). Under /api/admin/**, so ADMIN only.
 * VI: Hàng chờ email theo góc nhìn admin: mỗi trạng thái có bao nhiêu email, các email mới nhất của một trạng thái, và
 *     cách gửi lại email FAILED (sau khi sửa địa chỉ người gửi hoặc Brevo hết sự cố). Nằm dưới /api/admin/**, nên chỉ ADMIN.
 */
@RestController
@RequestMapping("/api/admin/emails")
public class AdminEmailController {

    private static final Set<String> STATUSES = Set.of("PENDING", "SENT", "FAILED", "SKIPPED");

    private final JdbcTemplate jdbc;

    AdminEmailController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** EN: No body: the list is for finding what went wrong. / VI: Không kèm nội dung thư: danh sách để tìm cái gì hỏng. */
    public record EmailRow(UUID id, UUID userId, String toEmail, String kind, String subject, String status,
            int attempts, String lastError, Instant createdAt, Instant sentAt) {
    }

    public record EmailQueue(Map<String, Integer> counts, List<EmailRow> items) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<EmailQueue> list(@RequestParam(defaultValue = "FAILED") String status) {
        if (!STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unknown email status " + status);
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        STATUSES.stream().sorted().forEach(each -> counts.put(each, 0));
        jdbc.query("SELECT status, COUNT(*) FROM email_outbox GROUP BY status",
                rs -> {
                    counts.put(rs.getString(1), rs.getInt(2));
                });
        List<EmailRow> items = jdbc.query("""
                SELECT id, user_id, to_email, kind, subject, status, attempts, last_error, created_at, sent_at
                FROM email_outbox WHERE status = ? ORDER BY created_at DESC LIMIT 100
                """, (rs, n) -> new EmailRow(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("to_email"), rs.getString("kind"), rs.getString("subject"), rs.getString("status"),
                rs.getInt("attempts"), rs.getString("last_error"), instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("sent_at"))), status);
        return ApiResponse.of(new EmailQueue(counts, items));
    }

    /**
     * EN: Puts a FAILED email back in the queue with a fresh count of attempts; the sender picks it up on its next run.
     * VI: Đưa email FAILED trở lại hàng chờ với số lần thử đặt lại từ đầu; job gửi sẽ nhận nó ở lượt chạy kế tiếp.
     */
    @PostMapping("/{id}/retry")
    @Transactional
    public ApiResponse<Void> retry(@PathVariable UUID id) {
        String status = jdbc.query("SELECT status FROM email_outbox WHERE id = ? FOR UPDATE",
                        (rs, n) -> rs.getString(1), id)
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMAIL_NOT_FOUND, "No email with id " + id));
        if (!"FAILED".equals(status)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_RETRYABLE, "Only a failed email can be sent again");
        }
        jdbc.update("""
                UPDATE email_outbox SET status = 'PENDING', attempts = 0, last_error = NULL, next_attempt_at = ?
                WHERE id = ?
                """, Timestamp.from(Instant.now()), id);
        return ApiResponse.of(null, "Email queued again");
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
