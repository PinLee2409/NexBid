package com.nexbid.user;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * EN: What other modules see of an account. A view, not the entity — nobody outside this module can modify it.
 * VI: Những gì module khác nhìn thấy về một tài khoản. Là bản xem, không phải entity — bên ngoài không sửa được.
 */
public record UserAccount(
        UUID id,
        String fullName,
        String email,
        Set<RoleName> roles,
        UserStatus status,
        Instant createdAt) {
}
