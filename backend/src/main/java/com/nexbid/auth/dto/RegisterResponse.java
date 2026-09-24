package com.nexbid.auth.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.nexbid.user.RoleName;
import com.nexbid.user.UserAccount;
import com.nexbid.user.UserStatus;

/**
 * EN: What registration returns. No password field exists, so none can ever be sent back by accident.
 * VI: Kết quả đăng ký. Không có field password, nên không bao giờ lỡ trả mật khẩu về client.
 */
public record RegisterResponse(
        UUID id,
        String fullName,
        String email,
        Set<RoleName> roles,
        UserStatus status,
        Instant createdAt) {

    public static RegisterResponse from(UserAccount account) {
        return new RegisterResponse(
                account.id(),
                account.fullName(),
                account.email(),
                account.roles(),
                account.status(),
                account.createdAt());
    }
}
