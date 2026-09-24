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
public record OrderView(Details order, PaymentView.Details payment, AuctionSummaryView auction) {

    public record Details(
            UUID id,
            UUID auctionId,
            UUID buyerId,
            UUID sellerId,
            UUID paymentId,
            BigDecimal amount,
            OrderStatus status,
            Instant createdAt,
            Instant updatedAt) {
    }
}
