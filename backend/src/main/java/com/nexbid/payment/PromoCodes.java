package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;

/**
 * EN: Shipping vouchers: admins make and switch off codes; buyers apply one at checkout, once each. A code counts as used
 *     when its payment succeeds.
 * VI: Voucher phí ship: admin tạo và tắt mã; người mua áp một mã khi thanh toán, mỗi người một lần. Mã tính là đã dùng
 *     khi khoản thanh toán của nó thành công.
 */
@Service
public class PromoCodes {

    private final JdbcTemplate jdbc;

    PromoCodes(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Promo(String code, BigDecimal amount, int maxUses, int used, Instant expiresAt, boolean active,
            String note, Instant createdAt) {
    }

    record Valid(String code, BigDecimal amount) {
    }

    static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /** EN: Letters, digits and dashes, 4 to 32. / VI: Chữ, số và gạch ngang, từ 4 tới 32 ký tự. */
    public Promo create(UUID adminId, String code, BigDecimal amount, int maxUses, Instant expiresAt, String note,
            Instant now) {
        String clean = normalise(code);
        if (!clean.matches("[A-Z0-9-]{4,32}")) {
            throw new BusinessException(ErrorCode.PROMO_INVALID, "Use 4 to 32 letters, digits or dashes");
        }
        if (amount == null || amount.signum() <= 0 || maxUses <= 0) {
            throw new BusinessException(ErrorCode.PROMO_INVALID, "The amount and the number of uses must be positive");
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.PROMO_INVALID, "The end date has passed");
        }
        try {
            jdbc.update("""
                    INSERT INTO promo_codes (code, amount, max_uses, expires_at, note, created_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, clean, amount, maxUses, expiresAt == null ? null : expiresAt.atOffset(ZoneOffset.UTC),
                    note == null || note.isBlank() ? null : note.trim(), adminId, now.atOffset(ZoneOffset.UTC));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.PROMO_EXISTS, "That code already exists");
        }
        return list().stream().filter(promo -> promo.code().equals(clean)).findFirst().orElseThrow();
    }

    public void setActive(String code, boolean active) {
        if (jdbc.update("UPDATE promo_codes SET active = ? WHERE code = ?", active, normalise(code)) == 0) {
            throw new BusinessException(ErrorCode.PROMO_INVALID, "No such code");
        }
    }

    public List<Promo> list() {
        return jdbc.query("""
                SELECT c.*, (SELECT COUNT(*) FROM promo_redemptions r WHERE r.code = c.code) AS used
                FROM promo_codes c ORDER BY c.created_at DESC
                """, (rs, n) -> new Promo(rs.getString("code"), rs.getBigDecimal("amount"), rs.getInt("max_uses"),
                rs.getInt("used"), rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toInstant(),
                rs.getBoolean("active"), rs.getString("note"), rs.getTimestamp("created_at").toInstant()));
    }

    /** EN: A code this buyer may use now, or why not. / VI: Một mã người mua này dùng được lúc này, hoặc lý do không. */
    Valid check(String code, UUID userId, Instant now) {
        String clean = normalise(code);
        Promo found = list().stream().filter(promo -> promo.code().equals(clean)).findFirst()
                .orElseThrow(() -> invalid("No such voucher"));
        if (!found.active() || (found.expiresAt() != null && !found.expiresAt().isAfter(now))) {
            throw invalid("This voucher has ended");
        }
        if (found.used() >= found.maxUses()) {
            throw invalid("This voucher has been used up");
        }
        Boolean usedBefore = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM promo_redemptions WHERE code = ? AND user_id = ?)", Boolean.class,
                clean, userId);
        if (Boolean.TRUE.equals(usedBefore)) {
            throw invalid("You have already used this voucher");
        }
        return new Valid(clean, found.amount());
    }

    /** EN: Recorded with the successful payment; a second use by the same buyer is ignored. / VI: Ghi cùng khoản thanh toán thành công; lần dùng thứ hai của cùng người mua bị bỏ qua. */
    void redeem(String code, UUID userId, UUID paymentId, BigDecimal discount, Instant at) {
        jdbc.update("""
                INSERT INTO promo_redemptions (code, user_id, payment_id, discount, created_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, code, userId, paymentId, discount, at.atOffset(ZoneOffset.UTC));
    }

    private static BusinessException invalid(String why) {
        return new BusinessException(ErrorCode.PROMO_INVALID, why);
    }
}
