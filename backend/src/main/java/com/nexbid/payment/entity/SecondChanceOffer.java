package com.nexbid.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nexbid.payment.OfferStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * EN: The one second-chance offer a lot can get (spec §17): to the runner-up, at their own highest bid, until a deadline.
 * VI: Đề nghị cơ hội thứ hai duy nhất một lô có thể có (spec §17): cho người thứ hai, với giá cao nhất của họ, tới một hạn chót.
 */
@Entity
@Table(name = "second_chance_offers")
public class SecondChanceOffer {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "auction_id", nullable = false, updatable = false)
    private UUID auctionId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private UUID sellerId;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    private UUID buyerId;

    @Column(nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OfferStatus status = OfferStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    protected SecondChanceOffer() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public SecondChanceOffer(
            UUID auctionId, UUID sellerId, UUID buyerId, BigDecimal amount, Instant createdAt, Instant expiresAt) {
        this.auctionId = auctionId;
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.amount = amount;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /** EN: The clock decides, as for payments. / VI: Đồng hồ quyết định, như với khoản thanh toán. */
    public boolean isOverdue(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public void settle(OfferStatus outcome, Instant at) {
        this.status = outcome;
        this.decidedAt = at;
    }

    public UUID getId() {
        return id;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public OfferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
