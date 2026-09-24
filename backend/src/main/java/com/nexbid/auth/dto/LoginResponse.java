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
public record LoginResponse(String accessToken, Instant expiresAt, UserSummary user) {

    public record UserSummary(
            UUID id, String fullName, String email, Set<RoleName> roles, UserStatus status) {
    }

    public static LoginResponse of(String token, Instant expiresAt, UserCredentials user) {
        return new LoginResponse(
                token,
                expiresAt,
                new UserSummary(user.id(), user.fullName(), user.email(), user.roles(), user.status()));
    }
}
