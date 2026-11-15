package com.nexbid.product.entity;

import java.time.Instant;
import java.util.UUID;

import com.nexbid.product.Parcel;
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

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "length_cm")
    private Integer lengthCm;

    @Column(name = "width_cm")
    private Integer widthCm;

    @Column(name = "height_cm")
    private Integer heightCm;

    // EN: The pieces of a set, as JSON; null for a single item. / VI: Các món của một bộ, dạng JSON; null với món đơn lẻ.
    @Column(name = "bundle_items", columnDefinition = "text")
    private String bundleItems;

    @Column(name = "video_url", length = 300)
    private String videoUrl;

    // EN: The photos, in order, are frames of a full turn. / VI: Các ảnh, theo thứ tự, là khung hình của một vòng xoay.
    @Column(name = "spin_view", nullable = false)
    private boolean spinView;

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public boolean isSpinView() {
        return spinView;
    }

    public void setSpinView(boolean spinView) {
        this.spinView = spinView;
    }

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

    /** EN: The packed size, or null when the seller gave none. / VI: Kích thước kiện, hoặc null khi người bán chưa khai. */
    private static final tools.jackson.databind.ObjectMapper JSON = tools.jackson.databind.json.JsonMapper.builder().build();

    public java.util.List<com.nexbid.product.BundleItem> getItems() {
        if (bundleItems == null || bundleItems.isBlank()) {
            return java.util.List.of();
        }
        return java.util.List.of(JSON.readValue(bundleItems, com.nexbid.product.BundleItem[].class));
    }

    public void setItems(java.util.List<com.nexbid.product.BundleItem> items) {
        this.bundleItems = items == null || items.isEmpty() ? null : JSON.writeValueAsString(items.stream()
                .map(item -> new com.nexbid.product.BundleItem(item.name().trim(), item.quantity())).toList());
    }

    public Parcel getParcel() {
        return weightGrams == null ? null : new Parcel(weightGrams, lengthCm, widthCm, heightCm);
    }

    public void setParcel(Parcel parcel) {
        this.weightGrams = parcel == null ? null : parcel.weightGrams();
        this.lengthCm = parcel == null ? null : parcel.lengthCm();
        this.widthCm = parcel == null ? null : parcel.widthCm();
        this.heightCm = parcel == null ? null : parcel.heightCm();
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
