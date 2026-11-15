package com.nexbid.auth.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.nexbid.user.RoleName;
import com.nexbid.user.UserCredentials;
import com.nexbid.user.UserStatus;

/**
 * EN: The token plus who it belongs to, so the client does not need a second call to render the header.
 * VI: Token kèm thông tin chủ nhân, để client khỏi gọi thêm lần nữa chỉ để vẽ header.
 */
public record LoginResponse(String accessToken, Instant expiresAt, UserSummary user,
        // EN: Set instead of the token when the password was right and a code from the authenticator app is needed.
        // VI: Có thay cho token khi mật khẩu đúng và cần thêm mã từ ứng dụng xác thực.
        String twoFactorChallenge) {

    public record UserSummary(
            UUID id, String fullName, String email, Set<RoleName> roles, UserStatus status) {
    }

    public static LoginResponse of(String token, Instant expiresAt, UserCredentials user) {
        return new LoginResponse(
                token,
                expiresAt,
                new UserSummary(user.id(), user.fullName(), user.email(), user.roles(), user.status()),
                null);
    }

    public static LoginResponse challenge(String twoFactorChallenge) {
        return new LoginResponse(null, null, null, twoFactorChallenge);
    }
}
