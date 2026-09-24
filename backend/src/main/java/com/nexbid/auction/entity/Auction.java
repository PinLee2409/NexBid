package com.nexbid.auction.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nexbid.auction.AuctionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * EN: One lot under the hammer (spec §7.4).
 * VI: Một lô hàng đưa lên sàn (spec §7.4).
 *
 * <p>EN: Ids to other modules are plain UUIDs, not entities — the auction module must not reach into
 *     product or user internals, and Spring Modulith would refuse the import.
 * <p>VI: Id trỏ sang module khác để dạng UUID trần chứ không phải entity — module auction không được thò
 *     tay vào ruột product hay user, và Spring Modulith sẽ từ chối import đó.
 */
@Entity
@Table(name = "auctions")
public class Auction {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    /**
     * EN: Copied from the product. A bid must check "is this the seller's own lot" on every attempt, and
     *     reading it here costs nothing instead of a join each time.
     * VI: Sao lại từ sản phẩm. Mỗi lượt trả giá đều phải kiểm "lô này có phải của chính người bán không",
     *     đọc ở đây không tốn gì thay vì phải join mỗi lần.
     */
    @Column(name = "seller_id", nullable = false, updatable = false)
    private UUID sellerId;

    @Column(name = "starting_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal startingPrice;

    /**
     * EN: Starts equal to the starting price and only ever rises. This is the number bidders must beat.
     * VI: Ban đầu bằng giá khởi điểm và chỉ tăng. Đây là con số người trả giá phải vượt qua.
     */
    @Column(name = "current_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "minimum_increment", nullable = false, precision = 15, scale = 2)
    private BigDecimal minimumIncrement;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    /** EN: Anti-sniping pushes this back, so it is not final until the lot closes. / VI: Chống bid phút chót đẩy mốc này lùi lại, nên nó chưa chốt cho tới khi lô đóng. */
    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuctionStatus status = AuctionStatus.DRAFT;

    @Column(name = "anti_sniping_enabled", nullable = false)
    private boolean antiSnipingEnabled;

    @Column(name = "anti_sniping_window_seconds", nullable = false)
    private int antiSnipingWindowSeconds = 30;

    @Column(name = "extension_seconds", nullable = false)
    private int extensionSeconds = 120;

    @Column(name = "extension_count", nullable = false)
    private int extensionCount;

    @Column(name = "bid_count", nullable = false)
    private int bidCount;

    @Column(name = "winner_id")
    private UUID winnerId;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /**
     * EN: Optimistic locking (spec §9). Two bids read the same price and both try to write; the second
     *     write fails and is retried against the price the first one set.
     * VI: Khoá lạc quan (spec §9). Hai lượt trả giá cùng đọc một mức giá rồi cùng ghi; lượt sau ghi hỏng
     *     và được thử lại trên mức giá mà lượt trước vừa đặt.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Auction() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public Auction(
            UUID productId,
            UUID sellerId,
            BigDecimal startingPrice,
            BigDecimal minimumIncrement,
            Instant startTime,
            Instant endTime,
            boolean antiSnipingEnabled,
            int antiSnipingWindowSeconds,
            int extensionSeconds) {

        this.productId = productId;
        this.sellerId = sellerId;
        this.startingPrice = startingPrice;
        this.currentPrice = startingPrice;
        this.minimumIncrement = minimumIncrement;
        this.startTime = startTime;
        this.endTime = endTime;
        this.antiSnipingEnabled = antiSnipingEnabled;
        this.antiSnipingWindowSeconds = antiSnipingWindowSeconds;
        this.extensionSeconds = extensionSeconds;
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

    public boolean isOwnedBy(UUID userId) {
        return this.sellerId.equals(userId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
        // EN: While nobody has bid, the current price is simply the opening price.
        // VI: Khi chưa ai trả giá, giá hiện tại chính là giá khởi điểm.
        if (this.bidCount == 0) {
            this.currentPrice = startingPrice;
        }
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public BigDecimal getMinimumIncrement() {
        return minimumIncrement;
    }

    public void setMinimumIncrement(BigDecimal minimumIncrement) {
        this.minimumIncrement = minimumIncrement;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public AuctionStatus getStatus() {
        return status;
    }

    public void setStatus(AuctionStatus status) {
        this.status = status;
    }

    public boolean isAntiSnipingEnabled() {
        return antiSnipingEnabled;
    }

    public void setAntiSnipingEnabled(boolean antiSnipingEnabled) {
        this.antiSnipingEnabled = antiSnipingEnabled;
    }

    public int getAntiSnipingWindowSeconds() {
        return antiSnipingWindowSeconds;
    }

    public void setAntiSnipingWindowSeconds(int antiSnipingWindowSeconds) {
        this.antiSnipingWindowSeconds = antiSnipingWindowSeconds;
    }

    public int getExtensionSeconds() {
        return extensionSeconds;
    }

    public void setExtensionSeconds(int extensionSeconds) {
        this.extensionSeconds = extensionSeconds;
    }

    public int getExtensionCount() {
        return extensionCount;
    }

    public int getBidCount() {
        return bidCount;
    }

    public UUID getWinnerId() {
        return winnerId;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
