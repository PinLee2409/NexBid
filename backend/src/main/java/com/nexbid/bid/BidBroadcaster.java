package com.nexbid.bid;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nexbid.auction.AuctionChannel;

/**
 * EN: Sends an accepted bid to everyone watching the lot (guide §23).
 * VI: Gửi một lượt trả giá đã được chấp nhận tới mọi người đang xem lô (guide §23).
 */
@Component
class BidBroadcaster {

    private final SimpMessagingTemplate messages;

    BidBroadcaster(SimpMessagingTemplate messages) {
        this.messages = messages;
    }

    /**
     * EN: After commit, never during (spec §46). Broadcasting inside the transaction would announce a
     *     price that a rollback could still take back — and the browsers would never hear the retraction.
     * VI: Sau khi commit, không bao giờ trong lúc đang chạy (spec §46). Phát tin trong transaction là thông
     *     báo một mức giá mà rollback vẫn có thể thu hồi — và trình duyệt sẽ không bao giờ nghe lời đính chính.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onBidPlaced(BidPlacedEvent event) {
        messages.convertAndSend(
                AuctionChannel.topicFor(event.placed().bid().auctionId()),
                BidPlacedMessage.of(event.placed()));
    }
}
