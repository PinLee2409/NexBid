package com.nexbid.order.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nexbid.order.OrderStatus;

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
 * EN: One sale: who bought what from whom, for how much. Only the status ever changes.
 * VI: Một giao dịch: ai mua gì của ai, với giá bao nhiêu. Chỉ có trạng thái là thay đổi.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "auction_id", nullable = false, updatable = false)
    private UUID auctionId;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    private UUID buyerId;

    @Column(name = "seller_id", nullable = false, updatable = false)
    private UUID sellerId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "ship_recipient", length = 120)
    private String shipRecipient;

    @Column(name = "ship_phone", length = 20)
    private String shipPhone;

    @Column(name = "ship_address", length = 640)
    private String shipAddress;

    @Column(length = 60)
    private String carrier;

    @Column(name = "tracking_number", length = 60)
    private String trackingNumber;

    @Column(name = "shipped_at")
    private Instant shippedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "auto_completed", nullable = false)
    private boolean autoCompleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public Order(UUID auctionId, UUID buyerId, UUID sellerId, UUID paymentId, BigDecimal amount) {
        this.auctionId = auctionId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.paymentId = paymentId;
        this.amount = amount;
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

    public void moveTo(OrderStatus status) {
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public UUID getBuyerId() {
        return buyerId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** EN: The buyer's address as it was when they paid. / VI: Địa chỉ người mua tại thời điểm thanh toán. */
    public void deliverTo(String recipient, String phone, String address) {
        this.shipRecipient = recipient;
        this.shipPhone = phone;
        this.shipAddress = address;
    }

    public void recordShipment(String carrier, String trackingNumber, Instant at) {
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.shippedAt = at;
    }

    public String getShipRecipient() {
        return shipRecipient;
    }

    public String getShipPhone() {
        return shipPhone;
    }

    public String getShipAddress() {
        return shipAddress;
    }

    public String getCarrier() {
        return carrier;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public Instant getShippedAt() {
        return shippedAt;
    }

    /**
     * EN: The sale is done: the buyer confirmed it, an admin closed a dispute, or the wait ran out.
     * VI: Giao dịch đã xong: người mua xác nhận, admin đóng khiếu nại, hoặc đã hết thời gian chờ.
     */
    public void complete(Instant at, boolean automatic) {
        this.status = com.nexbid.order.OrderStatus.COMPLETED;
        this.completedAt = at;
        this.autoCompleted = automatic;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isAutoCompleted() {
        return autoCompleted;
    }
}
