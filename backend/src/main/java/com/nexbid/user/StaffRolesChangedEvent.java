package com.nexbid.user;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * EN: An admin changed which staff roles an account holds. Audited.
 * VI: Một admin đổi các vai trò nhân viên của một tài khoản. Có ghi nhật ký.
 */
public record StaffRolesChangedEvent(UUID userId, UUID adminId, Set<RoleName> before, Set<RoleName> after,
        Instant at) {
}
