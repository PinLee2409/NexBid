package com.nexbid.trust;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nexbid.common.security.CurrentUser;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * EN: Notes where each signed-in account acts from — a hash of the network address, and the device id the browser
 *     sends in X-Device-Id — at most once an hour per account and origin. Never blocks a request: a failed write is
 *     logged and the request goes on.
 * VI: Ghi lại mỗi tài khoản đăng nhập thao tác từ đâu — mã băm địa chỉ mạng, và mã thiết bị trình duyệt gửi trong
 *     X-Device-Id — tối đa một lần mỗi giờ cho mỗi tài khoản và nguồn. Không bao giờ chặn request: ghi lỗi thì chỉ ghi
 *     log và request vẫn chạy tiếp.
 */
@Component
class OriginRecorder extends OncePerRequestFilter {

    static final String DEVICE_HEADER = "X-Device-Id";
    private static final Pattern DEVICE = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final Duration QUIET = Duration.ofHours(1);
    private static final int REMEMBERED = 20_000;
    private static final Logger log = LoggerFactory.getLogger(OriginRecorder.class);

    // EN: Absent in web-layer test slices, where nothing is recorded. / VI: Không có trong các test chỉ dựng tầng web, khi đó không ghi gì.
    private final ObjectProvider<JdbcTemplate> jdbc;
    private final Map<String, Instant> seen = new ConcurrentHashMap<>();

    OriginRecorder(ObjectProvider<JdbcTemplate> jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentUser user)) {
            return;
        }
        Instant now = Instant.now();
        note(user.id(), "NETWORK", hash(request.getRemoteAddr()), now);
        String device = request.getHeader(DEVICE_HEADER);
        if (device != null && DEVICE.matcher(device).matches()) {
            note(user.id(), "DEVICE", hash(device), now);
        }
    }

    void note(UUID userId, String kind, String value, Instant now) {
        String key = userId + "|" + kind + "|" + value;
        JdbcTemplate db = jdbc.getIfAvailable();
        if (db == null) {
            return;
        }
        Instant last = seen.get(key);
        if (last != null && last.plus(QUIET).isAfter(now)) {
            return;
        }
        if (seen.size() >= REMEMBERED) {
            seen.clear();
        }
        seen.put(key, now);
        try {
            db.update("""
                    INSERT INTO user_origins (user_id, kind, value, first_seen, last_seen) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (user_id, kind, value) DO UPDATE SET last_seen = EXCLUDED.last_seen
                    """, userId, kind, value, now.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC));
        } catch (RuntimeException ex) {
            log.warn("Could not note where user {} acts from: {}", userId, ex.getMessage());
        }
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
