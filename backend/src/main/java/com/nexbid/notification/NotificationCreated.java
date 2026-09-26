package com.nexbid.notification;

import java.util.UUID;

/**
 * EN: A notice was written for a user. Internal to this module: it only exists to ring the bell after commit.
 * VI: Một thông báo vừa được ghi cho một người. Chỉ dùng trong module: nó tồn tại để rung chuông sau khi commit.
 */
record NotificationCreated(UUID userId, NotificationView notification) {
}
