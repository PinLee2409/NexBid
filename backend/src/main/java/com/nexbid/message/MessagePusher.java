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
    record Pushed(String kind, UUID id, UUID orderId, boolean mine, String sender, String body, Instant createdAt) {
    }

    /** EN: The other side read up to {@code readAt}. / VI: Bên kia đã đọc tới {@code readAt}. */
    record ReadReceipt(String kind, UUID orderId, Instant readAt) {
    }

    /** EN: The other side is typing. / VI: Bên kia đang gõ. */
    record TypingSignal(String kind, UUID orderId) {
    }

    private final SimpMessagingTemplate messages;

    MessagePusher(SimpMessagingTemplate messages) {
        this.messages = messages;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSent(MessageEvents.Sent event) {
        messages.convertAndSendToUser(event.recipientId().toString(), MessageChannel.QUEUE,
                new Pushed("message", event.messageId(), event.orderId(), false, event.sender(), event.body(),
                        event.at()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onRead(MessageEvents.Read event) {
        messages.convertAndSendToUser(event.writerId().toString(), MessageChannel.QUEUE,
                new ReadReceipt("read", event.orderId(), event.at()));
    }

    @org.springframework.context.event.EventListener
    void onTyping(MessageEvents.Typing event) {
        messages.convertAndSendToUser(event.toId().toString(), MessageChannel.QUEUE,
                new TypingSignal("typing", event.orderId()));
    }
}
