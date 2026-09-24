package com.nexbid.user;

import java.util.Set;
import java.util.UUID;

/**
 * EN: An account plus its password hash. Named so it is obvious this is sensitive — only auth should ask for it.
 * VI: Tài khoản kèm hash mật khẩu. Đặt tên như vậy để thấy rõ đây là dữ liệu nhạy cảm — chỉ auth được phép hỏi.
 */
public record UserCredentials(
        UUID id,
        String fullName,
        String email,
        String passwordHash,
        UserStatus status,
        Set<RoleName> roles) {

    public UserAccount toAccount(java.time.Instant createdAt) {
        return new UserAccount(id, fullName, email, roles, status, createdAt);
    }
}
