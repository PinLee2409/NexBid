package com.nexbid.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: One notice as the bell shows it. {@code type} and {@code auctionId} let the page render its own
 *     wording and link, rather than depending on the stored English text.
 * VI: Một thông báo như cái chuông hiển thị. {@code type} và {@code auctionId} cho phép trang tự dựng câu
 *     chữ và đường dẫn, thay vì phụ thuộc vào đoạn tiếng Anh lưu sẵn.
 */
public record NotificationView(
        UUID id,
        NotificationType type,
        String title,
        String message,
        UUID auctionId,
        boolean read,
        Instant createdAt) {
}
