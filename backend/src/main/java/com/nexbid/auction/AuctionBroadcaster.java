package com.nexbid.auction;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * EN: Tells everyone watching a lot that its state changed (guide §25).
 * VI: Báo cho mọi người đang xem một lô biết trạng thái của nó đã đổi (guide §25).
 */
@Component
class AuctionBroadcaster {

    private final SimpMessagingTemplate messages;

    AuctionBroadcaster(SimpMessagingTemplate messages) {
        this.messages = messages;
    }

    /**
     * EN: After commit, never during. A page told "this lot is open" that then finds it still scheduled
     *     would show a bid button the server refuses.
     * VI: Sau khi commit, không bao giờ trong lúc đang chạy. Trang được báo "lô đã mở" mà hỏi lại thấy vẫn
     *     đang chờ giờ sẽ hiện nút trả giá mà server từ chối.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onLifecycleChange(AuctionLifecycleEvent event) {
        messages.convertAndSend(
                AuctionChannel.topicFor(event.auctionId()),
                AuctionLifecycleMessage.of(event));
    }
}
