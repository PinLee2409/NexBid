package com.nexbid.bid.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * EN: "Bid for me up to this much" on one lot (spec §14). The max is private to its owner.
 * VI: "Trả giá giúp tôi tới mức này" trên một lô (spec §14). Mức tối đa chỉ chủ nhân được biết.
 */
@Entity
@Table(name = "auto_bids")
public class AutoBid {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "auction_id", nullable = false, updatable = false)
    private UUID auctionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "max_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AutoBid() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public AutoBid(UUID auctionId, UUID userId, BigDecimal maxAmount) {
        this.auctionId = auctionId;
        this.userId = userId;
        this.maxAmount = maxAmount;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** EN: Switches it on with a new ceiling. / VI: Bật lên với mức trần mới. */
    public void activate(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
