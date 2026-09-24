package com.nexbid.auction;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: What a browser watching a lot receives when the clock moves it on (guide §25). Same channel as a
 *     bid, so one page needs one subscription for everything that can happen to the lot it is showing.
 * VI: Thứ trình duyệt đang xem một lô nhận được khi đồng hồ đẩy nó sang trạng thái khác (guide §25). Cùng
 *     kênh với lượt trả giá, nên một trang chỉ cần một đăng ký cho mọi chuyện có thể xảy ra với lô đó.
 */
public record AuctionLifecycleMessage(
        String type,
        UUID auctionId,
        AuctionStatus status,
        Instant startTime,
        Instant endTime,
        Instant serverTime) {

    static AuctionLifecycleMessage of(AuctionLifecycleEvent event) {
        return new AuctionLifecycleMessage(
                event.type(), event.auctionId(), event.status(),
                event.startTime(), event.endTime(), Instant.now());
    }
}
