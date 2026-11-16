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

    // EN: What the carrier last reported (GHN), and when it said delivered. / VI: Trạng thái đơn vị vận chuyển báo gần nhất (GHN), và lúc báo đã giao.
    @Column(name = "shipping_status", length = 40)
    private String shippingStatus;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "auto_completed", nullable = false)
    private boolean autoCompleted;

    // EN: GHN's district and ward of the address paid for, when it was picked from GHN's lists. / VI: Quận và phường GHN của địa chỉ đã trả tiền, khi chọn từ danh sách GHN.
    @Column(name = "ship_district_id")
    private Integer shipDistrictId;

    @Column(name = "ship_ward_code", length = 20)
    private String shipWardCode;

    // EN: Booked through NexBid's GHN account, which pays GHN; carrier_fee is what GHN charged. / VI: Đặt qua tài khoản GHN của NexBid, sàn trả tiền GHN; carrier_fee là số GHN thu.
    @Column(name = "ghn_booked", nullable = false)
    private boolean ghnBooked;

    @Column(name = "carrier_fee", precision = 15, scale = 2)
    private BigDecimal carrierFee;

    // EN: Free shipping: the seller pays the parcel. / VI: Miễn phí vận chuyển: người bán trả tiền gửi kiện.
    @Column(name = "seller_pays_shipping", nullable = false)
    private boolean sellerPaysShipping;

    // EN: Shipping the buyer paid on top of the amount; it goes to the seller with the goods. / VI: Phí ship người mua trả thêm ngoài số tiền hàng; trả cho người bán cùng tiền hàng.
    // EN: What the seller's own code took off the goods. / VI: Phần mã riêng của người bán trừ vào tiền hàng.
    @Column(name = "seller_discount", nullable = false, precision = 15, scale = 2)
    private BigDecimal sellerDiscount = BigDecimal.ZERO;

    public BigDecimal getSellerDiscount() {
        return sellerDiscount;
    }

    public void discountedBySeller(BigDecimal discount) {
        this.sellerDiscount = discount == null ? BigDecimal.ZERO : discount;
    }

    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

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

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    /**
     * EN: What the seller is paid for shipping: the buyer's shipping, unless NexBid booked GHN and paid for the parcel; minus
     *     that parcel when the seller offered free shipping.
     * VI: Phần phí ship trả cho người bán: phí ship người mua đã trả, trừ khi NexBid đặt GHN và đã trả tiền cho kiện.
     */
    public BigDecimal getSellerShipping() {
        if (ghnBooked) {
            // EN: With free shipping the seller owes the platform what GHN charged. / VI: Khi miễn phí vận chuyển, người bán nợ sàn số GHN đã thu.
            return sellerPaysShipping && carrierFee != null ? carrierFee.negate() : BigDecimal.ZERO;
        }
        return shippingFee;
    }

    public boolean isSellerPaysShipping() {
        return sellerPaysShipping;
    }

    public void sellerPaysShipping(boolean pays) {
        this.sellerPaysShipping = pays;
    }

    public Integer getShipDistrictId() {
        return shipDistrictId;
    }

    public String getShipWardCode() {
        return shipWardCode;
    }

    public boolean isGhnBooked() {
        return ghnBooked;
    }

    public BigDecimal getCarrierFee() {
        return carrierFee;
    }

    /** EN: GHN ids of the address paid for, or nulls for a typed one. / VI: Mã GHN của địa chỉ đã trả tiền, hoặc null với địa chỉ gõ tay. */
    public void deliverToGhn(Integer districtId, String wardCode) {
        this.shipDistrictId = districtId;
        this.shipWardCode = wardCode;
    }

    public void bookedWithGhn(BigDecimal fee) {
        this.ghnBooked = true;
        this.carrierFee = fee;
    }

    public void chargeShipping(BigDecimal fee) {
        this.shippingFee = fee == null ? BigDecimal.ZERO : fee;
    }

    /** EN: The buyer's address as it was when they paid. / VI: Địa chỉ người mua tại thời điểm thanh toán. */
    public void deliverTo(String recipient, String phone, String address) {
        this.shipRecipient = recipient;
        this.shipPhone = phone;
        this.shipAddress = address;
    }

    /** EN: A GHN booking was cancelled before pickup: the order waits to ship again. / VI: Đơn GHN đã huỷ trước khi lấy hàng: đơn chờ gửi lại. */
    public void cancelShipment() {
        this.carrier = null;
        this.trackingNumber = null;
        this.shippedAt = null;
        this.shippingStatus = null;
        this.ghnBooked = false;
        this.carrierFee = null;
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

    public String getShippingStatus() {
        return shippingStatus;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    /** EN: The carrier's latest word; the first "delivered" sets the delivery time. / VI: Thông tin mới nhất của đơn vị vận chuyển; lần "đã giao" đầu tiên đặt giờ giao. */
    public void recordCarrierStatus(String status, boolean delivered, Instant at) {
        this.shippingStatus = status;
        if (delivered && this.deliveredAt == null) {
            this.deliveredAt = at;
        }
    }
}
