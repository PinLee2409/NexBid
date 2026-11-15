package com.nexbid.message;

import java.time.Instant;
import java.util.UUID;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * EN: Sends a new message to the recipient's open pages after it commits, never before.
 * VI: Gửi tin mới tới các trang người nhận đang mở sau khi commit, không bao giờ trước đó.
 */
@Component
class MessagePusher {

    /** EN: What the socket carries; {@code mine} is always false for the recipient. / VI: Nội dung socket mang theo. */
    record Pushed(UUID id, UUID orderId, boolean mine, String sender, String body, Instant createdAt) {
    }

    private final SimpMessagingTemplate messages;

    MessagePusher(SimpMessagingTemplate messages) {
        this.messages = messages;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSent(MessageEvents.Sent event) {
        messages.convertAndSendToUser(event.recipientId().toString(), MessageChannel.QUEUE,
                new Pushed(event.messageId(), event.orderId(), false, event.sender(), event.body(), event.at()));
    }
}
