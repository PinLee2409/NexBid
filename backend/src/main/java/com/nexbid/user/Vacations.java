package com.nexbid.user;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;

/**
 * EN: A seller away from the shop. While away their page and lots say so, timed drafts wait instead of going to review,
 *     new submissions and automatic relisting stop. Lots already open keep running: their bidders were promised a
 *     close.
 * VI: Người bán đi vắng. Trong lúc vắng, trang và các lô của họ ghi rõ điều đó, bản nháp hẹn giờ chờ lại thay vì đi
 *     duyệt, việc gửi duyệt mới và tự đăng lại dừng. Lô đang mở vẫn chạy: người trả giá đã được hứa một phiên đóng.
 */
@Service
public class Vacations {

    static final int MAX_DAYS = 90;

    private final JdbcTemplate jdbc;

    Vacations(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** EN: {@code until} null means not away. / VI: {@code until} null là không vắng. */
    public record Vacation(Instant until, String note) {
    }

    /** EN: The seller's vacation, only while it lasts. / VI: Kỳ nghỉ của người bán, chỉ khi còn hiệu lực. */
    public Vacation of(UUID sellerId, Instant now) {
        return jdbc.query("SELECT vacation_until, vacation_note FROM users WHERE id = ?", (rs, n) -> {
            java.sql.Timestamp until = rs.getTimestamp(1);
            return until == null || !until.toInstant().isAfter(now) ? new Vacation(null, null)
                    : new Vacation(until.toInstant(), rs.getString(2));
        }, sellerId).stream().findFirst().orElse(new Vacation(null, null));
    }

    public boolean isAway(UUID sellerId, Instant now) {
        return of(sellerId, now).until() != null;
    }

    /**
     * EN: Starts (or changes) a vacation; {@code until} null ends it. At most 90 days ahead.
     * VI: Bắt đầu (hoặc đổi) kỳ nghỉ; {@code until} null là kết thúc. Tối đa 90 ngày tới.
     */
    public Vacation set(UUID sellerId, Instant until, String note, Instant now) {
        if (until == null) {
            jdbc.update("UPDATE users SET vacation_until = NULL, vacation_note = NULL WHERE id = ?", sellerId);
            return new Vacation(null, null);
        }
        if (!until.isAfter(now) || until.isAfter(now.plus(java.time.Duration.ofDays(MAX_DAYS)))) {
            throw new BusinessException(ErrorCode.VACATION_INVALID, "A vacation ends within the next 90 days");
        }
        String text = note == null || note.isBlank() ? null : note.strip();
        if (text != null && text.length() > 200) {
            text = text.substring(0, 200);
        }
        jdbc.update("UPDATE users SET vacation_until = ?, vacation_note = ? WHERE id = ?",
                until.atOffset(ZoneOffset.UTC), text, sellerId);
        return new Vacation(until, text);
    }

    /** EN: Refuses work a vacation pauses. / VI: Từ chối việc mà kỳ nghỉ tạm dừng. */
    public void requireAtWork(UUID sellerId, Instant now) {
        if (isAway(sellerId, now)) {
            throw new BusinessException(ErrorCode.SELLER_ON_VACATION,
                    "You are on vacation; end it in your seller settings to submit lots");
        }
    }
}
