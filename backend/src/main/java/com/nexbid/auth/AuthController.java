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

import jakarta.servlet.http.HttpServletRequest;
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
    private final PasswordResets passwordResets;

    public AuthController(AuthService authService, RefreshCookies cookies, PasswordResets passwordResets) {
        this.authService = authService;
        this.cookies = cookies;
        this.passwordResets = passwordResets;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(authService.register(request), "Account created");
    }

    /** EN: The access token in the body; the refresh token in an HttpOnly cookie. / VI: Access token trong body; refresh token trong cookie HttpOnly. */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest http, HttpServletResponse response) {

        // EN: The client's own address, even behind the frontend's proxy (server.forward-headers-strategy).
        // VI: Địa chỉ của chính client, kể cả khi đứng sau proxy của frontend (server.forward-headers-strategy).
        return signedIn(authService.login(request, http.getRemoteAddr()), response);
    }

    /** EN: The code step of two-step sign-in. / VI: Bước nhập mã của đăng nhập hai bước. */
    public record TwoFactorLoginRequest(
            @jakarta.validation.constraints.NotBlank String challenge,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 20) String code) {
    }

    @PostMapping("/login/2fa")
    public ApiResponse<LoginResponse> loginWithCode(@Valid @RequestBody TwoFactorLoginRequest request,
            HttpServletRequest http, HttpServletResponse response) {
        return signedIn(authService.completeTwoFactor(request.challenge(), request.code(), http.getRemoteAddr()),
                response);
    }

    /** EN: The ID token from Google's sign-in button. / VI: ID token từ nút đăng nhập Google. */
    public record GoogleLoginRequest(@jakarta.validation.constraints.NotBlank String credential, Boolean rememberMe) {
    }

    @PostMapping("/google")
    public ApiResponse<LoginResponse> google(@Valid @RequestBody GoogleLoginRequest request,
            HttpServletResponse response) {
        return signedIn(authService.google(request.credential(), request.rememberMe() == null || request.rememberMe()),
                response);
    }

    public record ForgotRequest(@jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 254) String email) {
    }

    /** EN: Always the same answer, whether or not the email has an account. / VI: Luôn trả lời giống nhau, dù email có tài khoản hay không. */
    @PostMapping("/password/forgot")
    public ApiResponse<Void> forgot(@Valid @RequestBody ForgotRequest request, HttpServletRequest http) {
        passwordResets.request(request.email(), http.getRemoteAddr(), java.time.Instant.now());
        return ApiResponse.ok("If that email has an account, a reset link is on its way");
    }

    public record ResetRequest(@jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(max = 100) String token,
            @jakarta.validation.constraints.NotBlank
            @jakarta.validation.constraints.Size(min = 8, max = 72) String password) {
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> reset(@Valid @RequestBody ResetRequest request) {
        passwordResets.reset(request.token(), request.password(), java.time.Instant.now());
        return ApiResponse.ok("Password changed; sign in with it");
    }

    /** EN: Which other ways to sign in are set up. / VI: Những cách đăng nhập khác đã được cài đặt. */
    @org.springframework.web.bind.annotation.GetMapping("/providers")
    public ApiResponse<java.util.Map<String, Object>> providers() {
        java.util.Map<String, Object> found = new java.util.HashMap<>();
        found.put("googleClientId", authService.googleClientId());
        return ApiResponse.of(found);
    }

    /** EN: The refresh cookie only once someone is fully signed in. / VI: Cookie refresh chỉ khi đã đăng nhập xong. */
    private ApiResponse<LoginResponse> signedIn(AuthService.Session session, HttpServletResponse response) {
        if (session.refresh() == null) {
            return ApiResponse.of(session.response(), "Code required");
        }
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
