package com.nexbid.order;

import java.math.BigDecimal;
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
    public record Shipped(UUID orderId, UUID actorId, UUID buyerId, UUID auctionId, String carrier, String trackingNumber) {
    }

    /**
     * EN: It completed: PROCESSING → COMPLETED, or DISPUTED → COMPLETED when an admin turned the dispute down.
     *     {@code actorId} is null when the wait ran out.
     * VI: Đơn đã hoàn tất: PROCESSING → COMPLETED, hoặc DISPUTED → COMPLETED khi admin bác khiếu nại.
     *     {@code actorId} là null khi hết thời gian chờ.
     */
    public record Received(UUID orderId, UUID actorId, OrderStatus from) {
    }

    /**
     * EN: The sale is final and the seller is owed the money. {@code automatic}: no one confirmed, the wait ran out.
     * VI: Giao dịch đã chốt và sàn nợ người bán khoản tiền. {@code automatic}: không ai xác nhận, đã hết thời gian chờ.
     */
    public record Completed(UUID orderId, UUID auctionId, UUID sellerId, UUID buyerId, BigDecimal amount,
            boolean automatic, Instant at) {
    }

    /** EN: The buyer reported a problem with an order. / VI: Người mua báo có vấn đề với một đơn. */
    public record Disputed(UUID disputeId, UUID orderId, UUID auctionId, UUID buyerId, UUID sellerId, String reason,
            Instant at) {
    }

    /** EN: An admin decided a dispute: money back, or the sale stands. / VI: Admin quyết định khiếu nại: hoàn tiền, hoặc giữ giao dịch. */
    public record DisputeResolved(UUID disputeId, UUID orderId, UUID auctionId, UUID buyerId, UUID sellerId,
            boolean refunded, String note, UUID actorId, Instant at) {
    }

    /** EN: The buyer reviewed the seller; the seller hears of it. / VI: Người mua đã đánh giá người bán; người bán được báo. */
    public record Reviewed(
            UUID reviewId, UUID orderId, UUID auctionId, UUID sellerId, UUID buyerId, int rating, Instant at) {
    }
}
