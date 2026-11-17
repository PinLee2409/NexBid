package com.nexbid.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.auth.refresh.RefreshCookies;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: Changing the password from the profile. This device stays signed in with a fresh refresh cookie; every other one is
 *     signed out.
 * VI: Đổi mật khẩu từ trang hồ sơ. Thiết bị này vẫn đăng nhập với cookie refresh mới; mọi thiết bị khác bị đăng xuất.
 */
@RestController
public class PasswordController {

    private final PasswordResets passwords;
    private final RefreshCookies cookies;

    public PasswordController(PasswordResets passwords, RefreshCookies cookies) {
        this.passwords = passwords;
        this.cookies = cookies;
    }

    public record ChangeRequest(@NotBlank @Size(max = 200) String currentPassword,
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    @PostMapping("/api/users/me/password")
    public ApiResponse<Void> change(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody ChangeRequest request,
            HttpServletRequest http, HttpServletResponse response) {
        var session = passwords.change(me.id(), request.currentPassword(), request.newPassword(), http.getRemoteAddr());
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.issue(session).toString());
        return ApiResponse.ok("Password changed; other devices were signed out");
    }
}
