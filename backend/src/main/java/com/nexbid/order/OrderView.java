package com.nexbid.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.payment.PaymentView;

/**
 * EN: An order with its payment and lot — the frontend's OrderEntry, field for field.
 * VI: Một đơn hàng kèm khoản thanh toán và lô của nó — đúng từng trường với OrderEntry của frontend.
 */
public record OrderView(Details order, PaymentView.Details payment, AuctionSummaryView auction, ReviewView review) {

    public record Details(
            UUID id,
            UUID auctionId,
            UUID buyerId,
            UUID sellerId,
            UUID paymentId,
            BigDecimal amount,
            OrderStatus status,
            Instant createdAt,
            Instant updatedAt,
            Shipping shipping,
            String carrier,
            String trackingNumber,
            Instant shippedAt,
            Instant completedAt,
            boolean autoCompleted,
            Dispute dispute) {
    }

    /**
     * EN: The newest complaint about the order, if any. reason: NOT_RECEIVED, NOT_AS_DESCRIBED, DAMAGED or OTHER;
     *     status: OPEN, REFUNDED or REJECTED.
     * VI: Khiếu nại mới nhất về đơn, nếu có. reason: NOT_RECEIVED, NOT_AS_DESCRIBED, DAMAGED hoặc OTHER; status: OPEN,
     *     REFUNDED hoặc REJECTED.
     */
    public record Dispute(UUID id, String reason, String details, String status, String resolutionNote,
            Instant createdAt, Instant resolvedAt) {
    }

    /** EN: Where it goes, as it was when the buyer paid. / VI: Nơi giao tới, theo lúc người mua thanh toán. */
    public record Shipping(String recipient, String phone, String address) {
    }
}
