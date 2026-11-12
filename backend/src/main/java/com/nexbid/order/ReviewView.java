package com.nexbid.order;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: A review as anyone may read it: the reviewer by public handle only, and the lot it was about.
 * VI: Một đánh giá mà ai cũng đọc được: người viết chỉ hiện tên công khai, kèm lô được đánh giá.
 */
public record ReviewView(
        UUID id,
        UUID orderId,
        UUID auctionId,
        int rating,
        String comment,
        String reviewer,
        String lotName,
        Instant createdAt) {
}
