package com.nexbid.user;

import java.util.UUID;

/** EN: An administrator changed an account's access. / VI: Quản trị viên đổi quyền truy cập của tài khoản. */
public record UserStatusChangedEvent(
        UUID userId, UUID actorId, UserStatus before, UserStatus after) {
}
