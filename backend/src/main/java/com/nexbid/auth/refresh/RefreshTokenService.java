package com.nexbid.auth.refresh;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.user.UserService;
import com.nexbid.user.UserStatus;
import com.nexbid.user.UserStatusChangedEvent;

/**
 * EN: Sessions that outlive the 15-minute access token (spec §7.2). A refresh token is used once: renewing
 *     replaces it with a new one in the same family. If a replaced token is ever presented again, someone
 *     else holds a copy, so the whole family is revoked and every holder must sign in again.
 * VI: Phiên đăng nhập sống lâu hơn access token 15 phút (spec §7.2). Mỗi refresh token chỉ dùng một lần: gia hạn
 *     sẽ thay nó bằng token mới cùng họ. Nếu token đã bị thay lại được gửi tới, tức là còn ai khác giữ bản sao,
 *     nên cả họ bị thu hồi và mọi người đang giữ đều phải đăng nhập lại.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** EN: A token as handed to the browser; its value is never stored. / VI: Token như được giao cho trình duyệt; giá trị của nó không bao giờ được lưu. */
    public record Issued(String value, Instant expiresAt, boolean remember) {
    }

    /** EN: A renewed session: whose it is, and the token replacing the one presented. / VI: Một phiên đã gia hạn: của ai, và token thay cho token vừa gửi. */
    public record Renewal(UUID userId, Issued next) {
    }

    private record Minted(RefreshToken row, Issued issued) {
    }

    private final RefreshTokenRepository tokens;
    private final UserService users;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository tokens, UserService users, RefreshTokenProperties properties) {
        this.tokens = tokens;
        this.users = users;
        this.ttl = properties.ttl();
    }

    /** EN: A sign-in: the first token of a new family. / VI: Một lần đăng nhập: token đầu tiên của một họ mới. */
    @Transactional
    public Issued start(UUID userId, boolean remember) {
        return mint(userId, UUID.randomUUID(), remember, Instant.now()).issued();
    }

    /**
     * EN: Empty when the token is unknown, expired, signed out, replayed, or its account is no longer active.
     *     Revocations made here commit even though the caller then refuses the request.
     * VI: Rỗng khi token không tồn tại, hết hạn, đã đăng xuất, bị dùng lại, hoặc tài khoản không còn hoạt động.
     *     Việc thu hồi ở đây vẫn được commit dù sau đó bên gọi từ chối request.
     */
    @Transactional
    public Optional<Renewal> renew(String presented) {
        if (presented == null || presented.isBlank()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Optional<RefreshToken> found = tokens.findLockedByHash(hash(presented));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = found.get();

        if (token.wasReplaced()) {
            tokens.revokeFamily(token.getFamilyId(), now);
            log.warn("A replaced refresh token came back: signed out session {} of user {}",
                    token.getFamilyId(), token.getUserId());
            return Optional.empty();
        }
        if (!token.isUsableAt(now)) {
            return Optional.empty();
        }
        boolean active = users.findById(token.getUserId())
                .map(account -> account.status() == UserStatus.ACTIVE)
                .orElse(false);
        if (!active) {
            tokens.revokeFamily(token.getFamilyId(), now);
            return Optional.empty();
        }

        Minted next = mint(token.getUserId(), token.getFamilyId(), token.isRemember(), now);
        token.replaceWith(next.row().getId(), now);
        return Optional.of(new Renewal(token.getUserId(), next.issued()));
    }

    /** EN: Signing out ends this session, on this device. / VI: Đăng xuất kết thúc phiên này, trên thiết bị này. */
    @Transactional
    public void end(String presented) {
        if (presented == null || presented.isBlank()) {
            return;
        }
        tokens.findLockedByHash(hash(presented))
                .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), Instant.now()));
    }

    /**
     * EN: Blocking an account ends every session it has, in the same transaction as the block itself.
     * VI: Khoá một tài khoản sẽ kết thúc mọi phiên của nó, trong cùng transaction với việc khoá.
     */
    @EventListener
    @Transactional
    void onStatusChanged(UserStatusChangedEvent event) {
        if (event.after() == UserStatus.BLOCKED) {
            int ended = tokens.revokeAllOf(event.userId(), Instant.now());
            log.info("Account {} blocked: {} refresh tokens revoked", event.userId(), ended);
        }
    }

    @Transactional
    public int pruneExpired() {
        return tokens.deleteExpired(Instant.now());
    }

    private Minted mint(UUID userId, UUID familyId, boolean remember, Instant now) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String value = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        Instant expiresAt = now.plus(ttl);
        RefreshToken row = tokens.save(new RefreshToken(userId, familyId, hash(value), remember, now, expiresAt));
        return new Minted(row, new Issued(value, expiresAt, remember));
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", ex);
        }
    }
}
