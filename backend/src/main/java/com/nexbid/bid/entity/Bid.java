package com.nexbid.bid.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * EN: One offer on one lot. Nothing here has a setter — a bid is a statement of record, and a record that
 *     can be edited afterwards is worth nothing.
 * VI: Một lượt trả giá cho một lô. Không có setter nào ở đây — lượt trả giá là bằng chứng, và bằng chứng
 *     sửa được về sau thì không còn giá trị gì.
 */
@Entity
@Table(name = "bids")
public class Bid {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "auction_id", nullable = false, updatable = false)
    private UUID auctionId;

    @Column(name = "bidder_id", nullable = false, updatable = false)
    private UUID bidderId;

    @Column(nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Bid() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public Bid(UUID auctionId, UUID bidderId, BigDecimal amount) {
        this.auctionId = auctionId;
        this.bidderId = bidderId;
        this.amount = amount;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getBidderId() {
        return bidderId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
