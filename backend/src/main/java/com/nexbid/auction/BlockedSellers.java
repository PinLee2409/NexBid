package com.nexbid.auction;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nexbid.user.UserStatus;
import com.nexbid.user.UserStatusChangedEvent;

/**
 * EN: Blocking a seller takes down their scheduled and live lots in the same transaction: nobody should keep bidding on
 *     goods from an account NexBid has shut. Unblocking does not put them back; the seller lists them again for approval.
 * VI: Khoá một người bán sẽ gỡ các lô đang chờ mở và đang mở của họ trong cùng transaction: không ai nên tiếp tục trả giá
 *     cho hàng của tài khoản NexBid đã khoá. Mở khoá không đưa chúng trở lại; người bán đăng lại để được duyệt.
 */
@Component
class BlockedSellers {

    static final String REASON = "The seller's account was suspended.";

    private final AuctionService auctions;

    BlockedSellers(AuctionService auctions) {
        this.auctions = auctions;
    }

    @EventListener
    void onStatusChanged(UserStatusChangedEvent event) {
        if (event.after() != UserStatus.BLOCKED) {
            return;
        }
        for (var auctionId : auctions.openLotsOf(event.userId())) {
            auctions.remove(auctionId, event.actorId(), REASON);
        }
    }
}
