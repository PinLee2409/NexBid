package com.nexbid.user.entity;

import java.time.Instant;
import java.util.UUID;

import com.nexbid.user.SellerApplicationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * EN: One request to become a seller (spec §7.1). Once decided it never changes again; a new attempt is a new row.
 * VI: Một yêu cầu trở thành người bán (spec §7.1). Đã quyết định thì không đổi nữa; thử lại là một dòng mới.
 */
@Entity
@Table(name = "seller_applications")
public class SellerApplication {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, length = 1000, updatable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SellerApplicationStatus status = SellerApplicationStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    protected SellerApplication() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public SellerApplication(UUID userId, String note, Instant createdAt) {
        this.userId = userId;
        this.note = note;
        this.createdAt = createdAt;
    }

    /** EN: The caller checks it is still PENDING. / VI: Bên gọi tự kiểm tra còn PENDING. */
    public void decide(SellerApplicationStatus outcome, String reason, UUID adminId, Instant at) {
        this.status = outcome;
        this.rejectionReason = reason;
        this.decidedBy = adminId;
        this.decidedAt = at;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getNote() {
        return note;
    }

    public SellerApplicationStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }
}
