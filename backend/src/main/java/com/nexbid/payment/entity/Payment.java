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

    // EN: Shipping charged on top of the amount, fixed once quoted until the buyer changes address. Zero until quoted.
    // VI: Phí ship thu thêm ngoài số tiền hàng, chốt khi báo phí và giữ tới khi người mua đổi địa chỉ. Bằng 0 khi chưa báo.
    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "shipping_source", length = 8)
    private String shippingSource;

    @Column(name = "shipping_address_key", length = 64)
    private String shippingAddressKey;

    // EN: Taken off the shipping by a voucher; the platform pays it, the seller still gets the full shipping.
    // VI: Phần voucher trừ vào phí ship; sàn trả phần này, người bán vẫn nhận đủ phí ship.
    @Column(name = "shipping_discount", nullable = false, precision = 15, scale = 2)
    private BigDecimal shippingDiscount = BigDecimal.ZERO;

    @Column(name = "promo_code", length = 32)
    private String promoCode;

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

    /** EN: Fixes the shipping for the address it was quoted for. / VI: Chốt phí ship cho địa chỉ đã dùng để báo phí. */
    public void quoteShipping(BigDecimal fee, String source, String addressKey) {
        this.shippingFee = fee;
        this.shippingSource = source;
        this.shippingAddressKey = addressKey;
    }

    /** EN: Whether a quote for this address is already fixed. / VI: Đã có phí chốt cho địa chỉ này chưa. */
    public boolean isQuotedFor(String addressKey) {
        return shippingSource != null && addressKey.equals(shippingAddressKey);
    }

    /**
     * EN: Goods plus shipping less the voucher: what VNPay charges and what a refund gives back.
     * VI: Tiền hàng cộng phí ship trừ voucher: số VNPay thu và số hoàn lại.
     */
    public BigDecimal getTotal() {
        return amount.add(shippingFee).subtract(getShippingDiscount());
    }

    /** EN: Never more than the shipping, whatever the address. / VI: Không bao giờ quá phí ship, dù địa chỉ nào. */
    public BigDecimal getShippingDiscount() {
        return shippingDiscount.min(shippingFee);
    }

    public String getPromoCode() {
        return promoCode;
    }

    public void applyPromo(String code, BigDecimal discount) {
        this.promoCode = code;
        this.shippingDiscount = discount == null ? BigDecimal.ZERO : discount;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public String getShippingSource() {
        return shippingSource;
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
