package com.nexbid.notification;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * EN: Rings the bell (spec §18): sends each new notice to its owner's realtime inbox.
 * VI: Rung chuông (spec §18): gửi mỗi thông báo mới tới hộp thư realtime của chủ nhân nó.
 */
@Component
class NotificationPusher {

    private final SimpMessagingTemplate messages;

    NotificationPusher(SimpMessagingTemplate messages) {
        this.messages = messages;
    }

    /**
     * EN: After commit, so the bell never rings for a notice a rollback takes back. Nobody connected is fine:
     *     the notice waits in the database for the next page load.
     * VI: Sau khi commit, để chuông không bao giờ rung cho thông báo bị rollback lấy lại. Không ai đang kết nối
     *     cũng không sao: thông báo nằm chờ trong database tới lần tải trang sau.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCreated(NotificationCreated event) {
        messages.convertAndSendToUser(event.userId().toString(), NotificationChannel.QUEUE, event.notification());
    }
}
