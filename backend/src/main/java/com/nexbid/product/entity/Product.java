package com.nexbid.product.entity;

import java.time.Instant;
import java.util.UUID;

import com.nexbid.product.ProductCondition;
import com.nexbid.product.ProductStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * EN: An item a seller owns (spec §7.3). It becomes a lot when an auction is scheduled for it.
 * VI: Một món hàng thuộc về người bán (spec §7.3). Nó thành lô đấu giá khi có phiên được lên lịch.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue
    private UUID id;

    /**
     * EN: The owner. Stored as a plain id, not a User entity — the product module must not reach into
     *     the user module's internals, and Spring Modulith would refuse the import anyway.
     * VI: Chủ sở hữu. Lưu dạng id trần chứ không phải entity User — module product không được thò tay vào
     *     ruột module user, và Spring Modulith cũng sẽ từ chối import đó.
     */
    @Column(name = "seller_id", nullable = false, updatable = false)
    private UUID sellerId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductCondition condition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProductStatus status = ProductStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public Product(
            UUID sellerId,
            Category category,
            String name,
            String description,
            ProductCondition condition,
            ProductStatus status) {
        this.sellerId = sellerId;
        this.category = category;
        this.name = name;
        this.description = description;
        this.condition = condition;
        this.status = status;
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

    /** EN: The rule behind every seller screen. / VI: Luật đứng sau mọi màn của người bán. */
    public boolean isOwnedBy(UUID userId) {
        return this.sellerId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProductCondition getCondition() {
        return condition;
    }

    public void setCondition(ProductCondition condition) {
        this.condition = condition;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
