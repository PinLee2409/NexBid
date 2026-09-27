package com.nexbid.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.auth.dto.LoginRequest;
import com.nexbid.auth.dto.LoginResponse;
import com.nexbid.auth.dto.RegisterRequest;
import com.nexbid.auth.dto.RegisterResponse;
import com.nexbid.auth.refresh.RefreshCookies;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.response.ApiResponse;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * EN: Public authentication endpoints. Everything here is reachable without a token, by definition.
 * VI: Các endpoint xác thực công khai. Theo đúng bản chất, chỗ này gọi được khi chưa có token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookies cookies;

    public AuthController(AuthService authService, RefreshCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(authService.register(request), "Account created");
    }

    /** EN: The access token in the body; the refresh token in an HttpOnly cookie. / VI: Access token trong body; refresh token trong cookie HttpOnly. */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.Session session = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.issue(session.refresh()).toString());
        return ApiResponse.of(session.response(), "Signed in");
    }

    /**
     * EN: A new access token from the refresh cookie, which is replaced by a new one at the same time.
     * VI: Một access token mới từ cookie refresh, và cookie đó cũng được thay bằng cookie mới cùng lúc.
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            HttpServletResponse response) {

        try {
            AuthService.Session session = authService.refresh(refreshToken);
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.issue(session.refresh()).toString());
            return ApiResponse.of(session.response(), "Session renewed");
        } catch (BusinessException ex) {
            // EN: A cookie that no longer works is removed, so the browser stops sending it.
            // VI: Cookie không còn dùng được thì bị xoá, để trình duyệt thôi gửi nó.
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
            throw ex;
        }
    }

    /** EN: Ends this device's session, whether or not it still had one. / VI: Kết thúc phiên của thiết bị này, dù nó còn phiên hay không. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken,
            HttpServletResponse response) {

        authService.logout(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
        return ApiResponse.ok("Signed out");
    }
}
