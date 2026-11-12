package com.nexbid.payment.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * EN: One attempt to pay through VNPay. PENDING until VNPay reports back, then SUCCESS or FAILED for good.
 * VI: Một lần thử thanh toán qua VNPay. PENDING cho tới khi VNPay báo lại, rồi SUCCESS hoặc FAILED và không đổi nữa.
 */
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    public static final String PENDING = "PENDING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    @Id
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Column(name = "txn_ref", nullable = false, updatable = false, length = 40)
    private String txnRef;

    @Column(nullable = false, updatable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 16)
    private String status = PENDING;

    @Column(name = "response_code", length = 8)
    private String responseCode;

    @Column(name = "transaction_no", length = 40)
    private String transactionNo;

    @Column(name = "bank_code", length = 32)
    private String bankCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentTransaction() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public PaymentTransaction(UUID paymentId, String txnRef, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.paymentId = paymentId;
        this.txnRef = txnRef;
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

    public boolean isPending() {
        return PENDING.equals(status);
    }

    public void settle(boolean success, String responseCode, String transactionNo, String bankCode) {
        this.status = success ? SUCCESS : FAILED;
        this.responseCode = responseCode;
        this.transactionNo = transactionNo;
        this.bankCode = bankCode;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public String getTxnRef() {
        return txnRef;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getStatus() {
        return status;
    }

    public String getResponseCode() {
        return responseCode;
    }
}
