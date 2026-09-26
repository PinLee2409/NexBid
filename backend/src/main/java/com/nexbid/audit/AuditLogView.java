package com.nexbid.audit;

import java.time.Instant;
import java.util.UUID;

/** EN: One audit entry for an admin. / VI: Một dòng nhật ký cho quản trị viên. */
public record AuditLogView(
        UUID id,
        UUID userId,
        String actorDisplayName,
        AuditAction action,
        String entityType,
        UUID entityId,
        String oldValue,
        String newValue,
        String ipAddress,
        Instant createdAt) {
}
