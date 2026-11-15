package com.nexbid.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auth.refresh.RefreshTokenService;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.mail.MailFacts;
import com.nexbid.mail.MailKind;
import com.nexbid.mail.Mailer;
import com.nexbid.user.UserCredentials;
import com.nexbid.user.UserService;
import com.nexbid.user.UserStatus;

/**
 * EN: Forgotten passwords and changed ones. Asking for a reset always answers the same, so the form cannot tell which
 *     emails have accounts; the link carries a random token kept only as a hash, good for 30 minutes and once. Setting a
 *     new password, either way, ends every session and emails the owner, so someone else's change does not go unseen.
 * VI: Quên mật khẩu và đổi mật khẩu. Yêu cầu đặt lại luôn trả lời giống nhau, để form không cho biết email nào có tài
 *     khoản; link mang một token ngẫu nhiên chỉ lưu dạng băm, dùng được 30 phút và một lần. Đặt mật khẩu mới, cách nào
 *     cũng vậy, sẽ kết thúc mọi phiên và gửi email cho chủ tài khoản, để việc người khác đổi không bị lọt.
 */
@Service
public class PasswordResets {

    static final Duration LIFETIME = Duration.ofMinutes(30);
    private static final int OPEN_LIMIT = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final UserService users;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshTokens;
    private final Mailer mailer;
    private final LoginAttempts attempts;
    private final String siteUrl;

    PasswordResets(JdbcTemplate jdbc, UserService users, PasswordEncoder encoder, RefreshTokenService refreshTokens,
            Mailer mailer, LoginAttempts attempts,
            @org.springframework.beans.factory.annotation.Value("${nexbid.mail.site-url}") String site) {
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.mailer = mailer;
        this.attempts = attempts;
        this.siteUrl = site.endsWith("/") ? site.substring(0, site.length() - 1) : site;
    }

    /**
     * EN: Emails a reset link if the address has an active account. Counted against the sign-in limit for that email and
     *     address, and at most three open links per account, so the form cannot flood an inbox.
     * VI: Gửi link đặt lại nếu địa chỉ có tài khoản đang hoạt động. Tính vào giới hạn đăng nhập theo email và địa chỉ đó,
     *     và tối đa ba link còn hiệu lực mỗi tài khoản, để form không làm ngập hộp thư.
     */
    @Transactional
    public void request(String email, String clientAddress, Instant now) {
        String clean = email == null ? "" : email.trim();
        attempts.checkAllowed(clean, clientAddress);
        attempts.recordFailure(clean, clientAddress);
        Optional<UserCredentials> found = users.findCredentialsByEmail(clean)
                .filter(user -> user.status() != UserStatus.BLOCKED);
        if (found.isEmpty()) {
            return;
        }
        UUID userId = found.get().id();
        Integer open = jdbc.queryForObject("""
                SELECT COUNT(*) FROM password_resets WHERE user_id = ? AND used_at IS NULL AND expires_at > ?
                """, Integer.class, userId, now.atOffset(ZoneOffset.UTC));
        if (open != null && open >= OPEN_LIMIT) {
            return;
        }
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        jdbc.update("""
                INSERT INTO password_resets (token_hash, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)
                """, hash(token), userId, now.plus(LIFETIME).atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC));
        mailer.send(userId, MailKind.PASSWORD_RESET,
                MailFacts.none().withReason(siteUrl + "/reset-password?token=" + token));
    }

    /**
     * EN: Sets a new password from a reset link: the token is spent, the account's other links die, every session ends.
     * VI: Đặt mật khẩu mới từ link đặt lại: token bị dùng hết, các link khác của tài khoản mất hiệu lực, mọi phiên kết thúc.
     */
    @Transactional
    public void reset(String token, String newPassword, Instant now) {
        UUID userId = jdbc.query("""
                SELECT user_id FROM password_resets
                WHERE token_hash = ? AND used_at IS NULL AND expires_at > ? FOR UPDATE
                """, rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                hash(token == null ? "" : token.trim()), now.atOffset(ZoneOffset.UTC));
        if (userId == null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_INVALID, "This link has expired or was already used");
        }
        jdbc.update("UPDATE password_resets SET used_at = ? WHERE user_id = ? AND used_at IS NULL",
                now.atOffset(ZoneOffset.UTC), userId);
        setPassword(userId, newPassword);
    }

    /**
     * EN: Changes the password of a signed-in account; the current one must be right. Returns the new session for this
     *     device, since every other one ends.
     * VI: Đổi mật khẩu của tài khoản đang đăng nhập; mật khẩu hiện tại phải đúng. Trả về phiên mới cho thiết bị này, vì mọi
     *     phiên khác đều kết thúc.
     */
    @Transactional
    public RefreshTokenService.Issued change(UUID userId, String current, String newPassword, String clientAddress) {
        UserCredentials user = users.findCredentialsById(userId).orElseThrow(() ->
                new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Sign in again"));
        attempts.checkAllowed(user.email(), clientAddress);
        if (current == null || !encoder.matches(current, user.passwordHash())) {
            attempts.recordFailure(user.email(), clientAddress);
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT, "The current password is not right");
        }
        if (encoder.matches(newPassword, user.passwordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_UNCHANGED, "Choose a password you are not using now");
        }
        setPassword(userId, newPassword);
        return refreshTokens.start(userId, true);
    }

    private void setPassword(UUID userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 8 || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Use 8 to 72 characters");
        }
        users.replacePassword(userId, encoder.encode(newPassword));
        refreshTokens.endAll(userId);
        mailer.send(userId, MailKind.PASSWORD_CHANGED, MailFacts.none());
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
