package com.nexbid.order;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: What people did to an order after it was paid. Kept inside the server: the audit log listens.
 * VI: Những gì người dùng làm với một đơn sau khi đã trả tiền. Chỉ dùng trong server: audit log lắng nghe.
 */
public final class OrderEvents {

    private OrderEvents() {
    }

    /** EN: The seller shipped it: PAID → PROCESSING. / VI: Người bán đã gửi hàng: PAID → PROCESSING. */
    public record Shipped(UUID orderId, UUID actorId, UUID buyerId, UUID auctionId) {
    }

    /** EN: The buyer received it: PROCESSING → COMPLETED. / VI: Người mua đã nhận hàng: PROCESSING → COMPLETED. */
    public record Received(UUID orderId, UUID actorId) {
    }

    /** EN: The buyer reviewed the seller; the seller hears of it. / VI: Người mua đã đánh giá người bán; người bán được báo. */
    public record Reviewed(
            UUID reviewId, UUID orderId, UUID auctionId, UUID sellerId, UUID buyerId, int rating, Instant at) {
    }
}
