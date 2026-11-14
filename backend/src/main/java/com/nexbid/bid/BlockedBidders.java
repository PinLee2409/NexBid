package com.nexbid.bid;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nexbid.bid.entity.AutoBid;
import com.nexbid.bid.repository.AutoBidRepository;
import com.nexbid.user.UserStatus;
import com.nexbid.user.UserStatusChangedEvent;

/**
 * EN: Blocking an account stops it bidding at all: sign-in is refused elsewhere, and here its standing auto-bids are
 *     switched off, in the same transaction, so the proxy can no longer bid for it. Unblocking does not switch them
 *     back on; the person sets them again.
 * VI: Khoá một tài khoản là dừng hẳn việc trả giá: đăng nhập bị từ chối ở nơi khác, còn ở đây các auto-bid đang bật của
 *     họ bị tắt, trong cùng transaction, để hệ thống không còn trả giá thay họ. Mở khoá không bật lại; người đó tự đặt lại.
 */
@Component
class BlockedBidders {

    private final AutoBidRepository autoBids;

    BlockedBidders(AutoBidRepository autoBids) {
        this.autoBids = autoBids;
    }

    @EventListener
    void onStatusChanged(UserStatusChangedEvent event) {
        if (event.after() != UserStatus.BLOCKED) {
            return;
        }
        var active = autoBids.findByUserIdAndActiveTrue(event.userId());
        active.forEach(AutoBid::deactivate);
        autoBids.saveAll(active);
    }
}
