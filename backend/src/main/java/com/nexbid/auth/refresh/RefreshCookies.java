package com.nexbid.auth.refresh;

import java.time.Duration;
import java.time.Instant;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * EN: The refresh token's cookie. HttpOnly, so page scripts (and anything injected into them) cannot read it;
 *     SameSite=Strict, so another site cannot make the browser send it; scoped to /api/auth, so it travels only
 *     to the refresh and logout endpoints.
 * VI: Cookie chứa refresh token. HttpOnly, để script trên trang (và mọi thứ bị chèn vào đó) không đọc được;
 *     SameSite=Strict, để trang khác không khiến trình duyệt gửi nó đi; giới hạn ở /api/auth, để nó chỉ đi tới
 *     endpoint refresh và logout.
 */
@Component
public class RefreshCookies {

    public static final String NAME = "nexbid_refresh";
    private static final String PATH = "/api/auth";

    private final boolean secure;

    RefreshCookies(RefreshTokenProperties properties) {
        this.secure = properties.secureCookie();
    }

    /**
     * EN: With "Remember me" the cookie lasts as long as the token; without it, until the browser closes.
     * VI: Có "Remember me" thì cookie sống bằng token; không có thì tới khi đóng trình duyệt.
     */
    public ResponseCookie issue(RefreshTokenService.Issued token) {
        ResponseCookie.ResponseCookieBuilder cookie = base(token.value());
        if (token.remember()) {
            cookie.maxAge(Duration.between(Instant.now(), token.expiresAt()));
        }
        return cookie.build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value).httpOnly(true).secure(secure).sameSite("Strict").path(PATH);
    }
}
