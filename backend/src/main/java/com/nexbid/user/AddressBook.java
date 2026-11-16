package com.nexbid.user;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EN: Each account's delivery address and email choices. Other modules read them; only the account's owner changes them.
 * VI: Địa chỉ nhận hàng và lựa chọn email của từng tài khoản. Module khác chỉ đọc; chỉ chủ tài khoản được thay đổi.
 */
@Service
public class AddressBook {

    private final JdbcTemplate jdbc;

    AddressBook(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ShippingAddress> addressOf(UUID userId) {
        List<ShippingAddress> found = jdbc.query(
                """
                SELECT recipient, phone, line, ward, district, province, province_id, district_id, ward_code
                FROM user_addresses WHERE user_id = ?
                """,
                (rs, row) -> new ShippingAddress(rs.getString("recipient"), rs.getString("phone"), rs.getString("line"),
                        rs.getString("ward"), rs.getString("district"), rs.getString("province"),
                        rs.getObject("province_id", Integer.class), rs.getObject("district_id", Integer.class),
                        rs.getString("ward_code")),
                userId);
        return found.stream().findFirst();
    }
    @Transactional
    public ShippingAddress save(UUID userId, ShippingAddress address) {
        ShippingAddress clean = address.trimmed();
        jdbc.update("""
                INSERT INTO user_addresses (user_id, recipient, phone, line, ward, district, province, province_id,
                    district_id, ward_code, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET recipient = EXCLUDED.recipient, phone = EXCLUDED.phone,
                    line = EXCLUDED.line, ward = EXCLUDED.ward, district = EXCLUDED.district,
                    province = EXCLUDED.province, province_id = EXCLUDED.province_id,
                    district_id = EXCLUDED.district_id, ward_code = EXCLUDED.ward_code, updated_at = EXCLUDED.updated_at
                """,
                userId, clean.recipient(), clean.phone(), clean.line(), clean.ward(), clean.district(), clean.province(),
                clean.provinceId(), clean.districtId(), clean.wardCode(), Timestamp.from(Instant.now()));
        return clean;
    }

    /** EN: Whether this account still wants outbid emails. / VI: Tài khoản này còn muốn nhận email bị vượt giá không. */
    public boolean wantsOutbidEmails(UUID userId) {
        Boolean wants = jdbc.query("SELECT outbid_emails FROM users WHERE id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, userId);
        return Boolean.TRUE.equals(wants);
    }

    @Transactional
    public void setOutbidEmails(UUID userId, boolean wanted) {
        jdbc.update("UPDATE users SET outbid_emails = ? WHERE id = ?", wanted, userId);
    }

    /** EN: Whether this account wants the daily new-lot email. / VI: Tài khoản này có muốn email lô mới hằng ngày không. */
    /**
     * EN: "vi", "en" or "both": the language the person's emails are written in.
     * VI: "vi", "en" hoặc "both": ngôn ngữ dùng để viết email cho người đó.
     */
    public String emailLanguageOf(UUID userId) {
        return jdbc.query("SELECT email_language FROM users WHERE id = ?", (rs, n) -> rs.getString(1), userId)
                .stream().findFirst().orElse("both");
    }

    /** EN: Anything but "vi", "en" or "both" is ignored. / VI: Giá trị khác "vi", "en" hoặc "both" bị bỏ qua. */
    public void setEmailLanguage(UUID userId, String language) {
        if (language != null && java.util.Set.of("vi", "en", "both").contains(language)) {
            jdbc.update("UPDATE users SET email_language = ? WHERE id = ?", language, userId);
        }
    }

    public boolean wantsDigestEmails(UUID userId) {
        Boolean wants = jdbc.query("SELECT digest_emails FROM users WHERE id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE, userId);
        return Boolean.TRUE.equals(wants);
    }

    @Transactional
    public void setDigestEmails(UUID userId, boolean wanted) {
        jdbc.update("UPDATE users SET digest_emails = ? WHERE id = ?", wanted, userId);
    }
}
