package com.nexbid.order.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * EN: A buyer's verdict on one completed order. Written once and never changed.
 * VI: Nhận định của người mua về một đơn đã hoàn tất. Viết một lần và không bao giờ đổi.
 */
@Entity
@Table(name = "seller_reviews")
public class Review {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "auction_id", nullable = false, updatable = false)
    private UUID auctionId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private UUID sellerId;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    private UUID buyerId;

    @Column(nullable = false, updatable = false)
    private short rating;

    @Column(length = 500, updatable = false)
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public Review(Order order, int rating, String comment, Instant createdAt) {
        this.orderId = order.getId();
        this.auctionId = order.getAuctionId();
        this.sellerId = order.getSellerId();
        this.buyerId = order.getBuyerId();
        this.rating = (short) rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public int getRating() {
        return rating;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
