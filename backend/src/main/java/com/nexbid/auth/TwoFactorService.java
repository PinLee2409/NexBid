package com.nexbid.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auth.jwt.JwtProperties;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * EN: Two-step sign-in with an authenticator app. Setting up keeps a fresh secret until the first code proves the app
 *     has it; then sign-in asks for a code after the password, through a short-lived challenge that is useless as an
 *     access token. Eight recovery codes, each good once, stand in for a lost phone.
 * VI: Đăng nhập hai bước bằng ứng dụng xác thực. Lúc cài đặt giữ một secret mới tới khi mã đầu tiên chứng minh ứng dụng
 *     đã có nó; sau đó đăng nhập hỏi mã sau mật khẩu, qua một challenge ngắn hạn không dùng làm access token được. Tám mã
 *     khôi phục, mỗi mã dùng một lần, thay cho điện thoại bị mất.
 */
@Service
public class TwoFactorService {

    private static final String ISSUER = "NexBid";
    private static final String CHALLENGE_ISSUER = "nexbid-2fa";
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final int RECOVERY_CODES = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final SecretKey challengeKey;
    private final SecretKeySpec secretKey;
    private final LoginAttempts attempts;

    TwoFactorService(JdbcTemplate jdbc, ApplicationEventPublisher events, JwtProperties jwt, LoginAttempts attempts) {
        this.jdbc = jdbc;
        this.attempts = attempts;
        this.events = events;
        // EN: Both keys come from the JWT secret but differ from the access token key, so neither token can pass as the other.
        // VI: Cả hai khoá suy ra từ JWT secret nhưng khác khoá của access token, nên token này không giả làm token kia được.
        this.challengeKey = Keys.hmacShaKeyFor(sha256(jwt.secret() + ":2fa-challenge"));
        this.secretKey = new SecretKeySpec(sha256(jwt.secret() + ":totp-secret"), "AES");
    }

    /** EN: Announced to the audit log. / VI: Loan báo cho nhật ký hệ thống. */
    public record Changed(UUID userId, boolean enabled) {
    }

    /** EN: What the app needs: the key to type, and the otpauth link to tap or scan. / VI: Thứ ứng dụng cần: khoá để gõ, và link otpauth để chạm hoặc quét. */
    public record Setup(String secret, String otpauthUri) {
    }

    public boolean enabled(UUID userId) {
        return Boolean.TRUE.equals(jdbc.query("SELECT enabled_at IS NOT NULL FROM user_totp WHERE user_id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : false, userId));
    }

    /**
     * EN: A fresh secret, kept until enabled. Refused while two-step sign-in is already on.
     * VI: Một secret mới, giữ tới khi bật. Bị từ chối khi đăng nhập hai bước đang bật.
     */
    @Transactional
    public Setup setup(UUID userId, String email) {
        if (enabled(userId)) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_ALREADY_ON, "Two-step sign-in is already on");
        }
        String secret = Totp.newSecret();
        jdbc.update("""
                INSERT INTO user_totp (user_id, secret) VALUES (?, ?)
                ON CONFLICT (user_id) DO UPDATE SET secret = EXCLUDED.secret, last_step = 0, recovery_codes = '{}'
                """, userId, encrypt(secret));
        String label = URLEncoder.encode(ISSUER + ":" + email, StandardCharsets.UTF_8).replace("+", "%20");
        return new Setup(secret, "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + ISSUER
                + "&algorithm=SHA1&digits=6&period=30");
    }

    /**
     * EN: Turns it on once a code from the app matches; returns the recovery codes, shown this once.
     * VI: Bật khi mã từ ứng dụng khớp; trả về các mã khôi phục, chỉ hiện lần này.
     */
    @Transactional
    public List<String> enable(UUID userId, String code, Instant now) {
        Row row = row(userId).filter(found -> found.enabledAt() == null).orElseThrow(() ->
                new BusinessException(ErrorCode.TWO_FACTOR_NOT_SET_UP, "Start the setup again"));
        long step = Totp.match(decrypt(row.secret()), code, now, row.lastStep());
        if (step < 0) {
            throw new BusinessException(ErrorCode.TWO_FACTOR_CODE_INVALID, "That code does not match");
        }
        List<String> codes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String recovery = HexFormat.of().formatHex(randomBytes(5)).toUpperCase();
            codes.add(recovery.substring(0, 5) + "-" + recovery.substring(5));
            hashes.add(hash(recovery));
        }
        jdbc.update("UPDATE user_totp SET enabled_at = ?, last_step = ?, recovery_codes = ? WHERE user_id = ?",
                now.atOffset(ZoneOffset.UTC), step, hashes.toArray(String[]::new), userId);
        events.publishEvent(new Changed(userId, true));
        return codes;
    }

    /** EN: Turns it off; a current code or a recovery code is needed. / VI: Tắt; cần một mã hiện tại hoặc mã khôi phục. */
    @Transactional
    public void disable(UUID userId, String code, Instant now) {
        attempts.checkCodes(userId);
        if (!verify(userId, code, now)) {
            attempts.recordCodeFailure(userId);
            throw new BusinessException(ErrorCode.TWO_FACTOR_CODE_INVALID, "That code does not match");
        }
        jdbc.update("DELETE FROM user_totp WHERE user_id = ?", userId);
        events.publishEvent(new Changed(userId, false));
    }

    /**
     * EN: Checks a sign-in code: the app's current code (each used once), or a recovery code (then used up).
     * VI: Kiểm mã đăng nhập: mã hiện tại của ứng dụng (mỗi mã dùng một lần), hoặc mã khôi phục (dùng xong là hết).
     */
    @Transactional
    public boolean verify(UUID userId, String code, Instant now) {
        Optional<Row> found = row(userId).filter(row -> row.enabledAt() != null);
        if (found.isEmpty() || code == null) {
            return false;
        }
        Row row = found.get();
        String typed = code.replace(" ", "").trim();
        long step = Totp.match(decrypt(row.secret()), typed, now, row.lastStep());
        if (step >= 0) {
            return jdbc.update("UPDATE user_totp SET last_step = ? WHERE user_id = ? AND last_step < ?",
                    step, userId, step) == 1;
        }
        String recovery = hash(typed.replace("-", "").toUpperCase());
        return jdbc.update("""
                UPDATE user_totp SET recovery_codes = array_remove(recovery_codes, ?)
                WHERE user_id = ? AND ? = ANY (recovery_codes)
                """, recovery, userId, recovery) == 1;
    }

    /** EN: Proof the password was right, for the code step. / VI: Bằng chứng mật khẩu đã đúng, cho bước nhập mã. */
    String challengeFor(UUID userId, boolean remember, Instant now) {
        return Jwts.builder().subject(userId.toString()).claim("remember", remember).issuer(CHALLENGE_ISSUER)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(CHALLENGE_TTL)))
                .signWith(challengeKey).compact();
    }

    record Challenge(UUID userId, boolean remember) {
    }

    Optional<Challenge> readChallenge(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(challengeKey).requireIssuer(CHALLENGE_ISSUER).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.of(new Challenge(UUID.fromString(claims.getSubject()),
                    Boolean.TRUE.equals(claims.get("remember", Boolean.class))));
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private record Row(String secret, Instant enabledAt, long lastStep) {
    }

    private Optional<Row> row(UUID userId) {
        return jdbc.query("SELECT secret, enabled_at, last_step FROM user_totp WHERE user_id = ? FOR UPDATE",
                (rs, n) -> new Row(rs.getString("secret"),
                        rs.getTimestamp("enabled_at") == null ? null : rs.getTimestamp("enabled_at").toInstant(),
                        rs.getLong("last_step")), userId).stream().findFirst();
    }

    private String encrypt(String plain) {
        try {
            byte[] iv = randomBytes(12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = Arrays.copyOf(iv, iv.length + sealed.length);
            System.arraycopy(sealed, 0, out, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Two-step secret could not be read; was NEXBID_JWT_SECRET changed?", ex);
        }
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String hash(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
