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
/**
 * EN: {@code buyer} is filled only for the seller and the admin: the seller's rating of this buyer and the buyer's record.
 * VI: {@code buyer} chỉ có cho người bán và admin: đánh giá của người bán về người mua này và hồ sơ của người mua.
 */
public record OrderView(Details order, PaymentView.Details payment, AuctionSummaryView auction, ReviewView review,
        BuyerSide buyer) {

    public record BuyerSide(BuyerReview review, BuyerRecord record) {
    }

    /** EN: The seller's rating of the buyer on this order. / VI: Đánh giá của người bán về người mua trên đơn này. */
    public record BuyerReview(int rating, String comment, Instant createdAt) {
    }

    /**
     * EN: Purchases completed, wins never paid for, and the average sellers gave (null before the first rating).
     * VI: Số đơn hoàn tất, số lần thắng không trả tiền, và điểm trung bình người bán cho (null khi chưa có đánh giá).
     */
    public record BuyerRecord(int completed, int unpaidWins, BigDecimal averageRating, int ratings) {
    }

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
            Dispute dispute,
            // EN: The carrier's latest status and delivery time, when tracked (GHN). / VI: Trạng thái mới nhất và giờ giao của đơn vị vận chuyển, khi được theo dõi (GHN).
            String shippingStatus,
            Instant deliveredAt) {
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
