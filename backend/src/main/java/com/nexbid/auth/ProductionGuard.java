package com.nexbid.auth;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.nexbid.auth.jwt.JwtProperties;
import com.nexbid.auth.refresh.RefreshTokenProperties;

/**
 * EN: With the `prod` profile, refuses to start on settings that are only safe on a developer's machine: the
 *     JWT secret shipped in the repository (or one too short for HMAC-SHA256), and a refresh cookie allowed
 *     over plain HTTP. Failing at startup beats running a server anyone can forge tokens for.
 * VI: Với profile `prod`, từ chối khởi động nếu cấu hình chỉ an toàn trên máy của dev: JWT secret có sẵn trong
 *     repository (hoặc quá ngắn cho HMAC-SHA256), và cookie refresh được phép đi qua HTTP thường. Dừng ngay lúc
 *     khởi động còn hơn chạy một server mà ai cũng làm giả được token.
 */
@Component
@Profile("prod")
class ProductionGuard {

    /** EN: HMAC-SHA256 wants a key at least as long as its output. / VI: HMAC-SHA256 cần khoá ít nhất dài bằng đầu ra của nó. */
    static final int MIN_SECRET_BYTES = 32;

    ProductionGuard(JwtProperties jwt, RefreshTokenProperties refresh) {
        List<String> problems = problemsWith(jwt.secret(), refresh.secureCookie());
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start with the 'prod' profile: " + String.join("; ", problems));
        }
    }

    static List<String> problemsWith(String secret, boolean secureCookie) {
        List<String> problems = new ArrayList<>();
        if (secret == null || secret.contains("change-me")) {
            problems.add("NEXBID_JWT_SECRET is a development default; set a random secret of your own");
        } else if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            problems.add("NEXBID_JWT_SECRET is shorter than " + MIN_SECRET_BYTES + " bytes");
        }
        if (!secureCookie) {
            problems.add("NEXBID_REFRESH_COOKIE_SECURE is false; the refresh cookie must only travel over HTTPS");
        }
        return problems;
    }
}
