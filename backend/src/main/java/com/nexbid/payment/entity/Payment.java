package com.nexbid.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nexbid.payment.PaymentStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * EN: What a winner owes for one lot, and by when. Amount and deadline are fixed at creation.
 * VI: Số tiền người thắng nợ cho một lô, và hạn chót. Số tiền và hạn chót cố định từ lúc tạo.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "auction_id", nullable = false, updatable = false)
    private UUID auctionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "expired_at", nullable = false, updatable = false)
    private Instant expiredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public Payment(UUID auctionId, UUID userId, BigDecimal amount, Instant expiredAt) {
        this.auctionId = auctionId;
        this.userId = userId;
        this.amount = amount;
        this.expiredAt = expiredAt;
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

    /** EN: The clock decides, not the status — a PENDING row past its deadline is already too late. / VI: Đồng hồ quyết định chứ không phải trạng thái — dòng PENDING đã quá hạn là đã trễ. */
    public boolean isOverdue(Instant now) {
        return !now.isBefore(expiredAt);
    }

    public void settle(PaymentStatus outcome) {
        this.status = outcome;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
