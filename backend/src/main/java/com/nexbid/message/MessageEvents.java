package com.nexbid.message;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: What happened in a conversation. {@code firstUnread} is true when the recipient had nothing unread from the
 *     sender, so a burst of messages makes one notification, not twenty.
 * VI: Chuyện gì xảy ra trong cuộc trò chuyện. {@code firstUnread} là true khi người nhận chưa có tin chưa đọc nào từ
 *     người gửi, để một tràng tin chỉ tạo một thông báo, không phải hai mươi.
 */
public final class MessageEvents {

    private MessageEvents() {
    }

    public record Sent(UUID messageId, UUID orderId, UUID auctionId, UUID senderId, UUID recipientId, String sender,
            String body, Instant at, boolean firstUnread) {
    }
}
