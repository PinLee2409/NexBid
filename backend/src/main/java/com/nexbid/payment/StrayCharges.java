package com.nexbid.payment;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.payment.entity.Payment;
import com.nexbid.payment.repository.PaymentRepository;
import com.nexbid.user.BankAccount;
import com.nexbid.user.BankAccounts;
import com.nexbid.user.UserAccount;
import com.nexbid.user.UserService;

/**
 * EN: Charges VNPay took that no payment could use: confirmed after the window closed (LATE), or a second charge for a
 *     payment already paid (DUPLICATE). The buyer hears at once; an admin transfers the money back by hand and records
 *     the bank reference, exactly like an order refund.
 * VI: Khoản VNPay đã thu mà không khoản thanh toán nào dùng được: xác nhận sau khi hết hạn (LATE), hoặc thu lần hai cho
 *     khoản đã trả (DUPLICATE). Người mua được báo ngay; admin tự chuyển khoản trả lại và ghi mã giao dịch, giống hệt
 *     hoàn tiền đơn hàng.
 */
@Service
public class StrayCharges {

    public enum Reason { LATE, DUPLICATE }

    /** EN: One stray charge, with the lot and the buyer for the admin page. / VI: Một khoản thu thừa, kèm lô và người mua cho trang admin. */
    public record StrayCharge(UUID id, UUID paymentId, UUID auctionId, UUID buyerId, String buyerName,
            String buyerEmail, String txnRef, BigDecimal amount, Reason reason, Instant createdAt, Instant refundedAt,
            String reference, BankAccount refundedTo, BankAccount buyerAccount, AuctionSummaryView auction) {
    }

    private final JdbcTemplate jdbc;
    private final PaymentRepository payments;
    private final AuctionService auctions;
    private final UserService users;
    private final BankAccounts bankAccounts;
    private final ApplicationEventPublisher events;

    StrayCharges(JdbcTemplate jdbc, PaymentRepository payments, AuctionService auctions, UserService users,
            BankAccounts bankAccounts, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.payments = payments;
        this.auctions = auctions;
        this.users = users;
        this.bankAccounts = bankAccounts;
        this.events = events;
    }

    /** EN: Notes the charge once per VNPay attempt and tells the buyer. / VI: Ghi khoản thu một lần cho mỗi lần trả VNPay và báo người mua. */
    @Transactional
    void record(UUID paymentId, String txnRef, BigDecimal amount, Reason reason, Instant at) {
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO stray_charges (id, payment_id, txn_ref, amount, reason, created_at)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (txn_ref) DO NOTHING
                """, id, paymentId, txnRef, amount, reason.name(), utc(at));
        if (inserted == 0) {
            return;
        }
        Payment payment = payments.findById(paymentId).orElseThrow();
        events.publishEvent(new PaymentEvents.StrayCharged(id, paymentId, payment.getAuctionId(), payment.getUserId(),
                amount, reason.name(), at));
    }

    /** EN: Waiting ones first, oldest first; then the latest refunded. / VI: Khoản đang chờ trước, cũ nhất trước; rồi tới các khoản đã hoàn gần nhất. */
    @Transactional(readOnly = true)
    public List<StrayCharge> list() {
        record Row(UUID id, UUID paymentId, String txnRef, BigDecimal amount, Reason reason, Instant createdAt,
                Instant refundedAt, String reference, BankAccount refundedTo) {
        }
        List<Row> rows = jdbc.query("""
                (SELECT * FROM stray_charges WHERE refunded_at IS NULL ORDER BY created_at LIMIT 200)
                UNION ALL
                (SELECT * FROM stray_charges WHERE refunded_at IS NOT NULL ORDER BY refunded_at DESC LIMIT 50)
                """, (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("payment_id", UUID.class),
                rs.getString("txn_ref"), rs.getBigDecimal("amount"), Reason.valueOf(rs.getString("reason")),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("refunded_at")),
                rs.getString("reference"), account(rs)));
        Map<UUID, Payment> byId = payments.findAllById(rows.stream().map(Row::paymentId).distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(Payment::getId, payment -> payment));
        Map<UUID, AuctionSummaryView> lots = auctions.participantSummariesOf(
                byId.values().stream().map(Payment::getAuctionId).distinct().toList());
        return rows.stream().map(row -> {
            Payment payment = byId.get(row.paymentId());
            Optional<UserAccount> buyer = users.findById(payment.getUserId());
            return new StrayCharge(row.id(), row.paymentId(), payment.getAuctionId(), payment.getUserId(),
                    buyer.map(UserAccount::fullName).orElse(""), buyer.map(UserAccount::email).orElse(""),
                    row.txnRef(), row.amount(), row.reason(), row.createdAt(), row.refundedAt(), row.reference(),
                    row.refundedTo(), bankAccounts.of(payment.getUserId()).orElse(null),
                    lots.get(payment.getAuctionId()));
        }).toList();
    }

    /**
     * EN: The admin has transferred the money back to the buyer's refund account; records it with the bank reference.
     * VI: Admin đã chuyển khoản trả lại vào tài khoản nhận hoàn tiền của người mua; ghi lại kèm mã giao dịch ngân hàng.
     */
    @Transactional
    public void refund(UUID adminId, UUID strayId, String reference) {
        record Open(UUID paymentId, BigDecimal amount, boolean refunded) {
        }
        Open stray = jdbc.query("SELECT payment_id, amount, refunded_at FROM stray_charges WHERE id = ? FOR UPDATE",
                        (rs, n) -> new Open(rs.getObject(1, UUID.class), rs.getBigDecimal(2), rs.getTimestamp(3) != null),
                        strayId)
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STRAY_CHARGE_NOT_FOUND,
                        "No stray charge with id " + strayId));
        if (stray.refunded()) {
            throw new BusinessException(ErrorCode.STRAY_CHARGE_REFUNDED, "This charge has already been refunded");
        }
        Payment payment = payments.findById(stray.paymentId()).orElseThrow();
        BankAccount account = bankAccounts.of(payment.getUserId()).orElseThrow(() -> new BusinessException(
                ErrorCode.REFUND_ACCOUNT_REQUIRED, "The buyer has not added a bank account for refunds yet"));

        Instant now = Instant.now();
        String ref = reference.trim();
        jdbc.update("""
                UPDATE stray_charges SET refunded_at = ?, reference = ?, bank_name = ?, account_number = ?,
                       account_holder = ?, refunded_by = ? WHERE id = ?
                """, utc(now), ref, account.bankName(), account.accountNumber(), account.accountHolder(), adminId,
                strayId);
        events.publishEvent(new PaymentEvents.StrayRefunded(strayId, payment.getId(), payment.getAuctionId(),
                payment.getUserId(), stray.amount(), ref, account.masked(), adminId, now));
    }

    private static BankAccount account(ResultSet rs) throws SQLException {
        String bank = rs.getString("bank_name");
        return bank == null ? null
                : new BankAccount(bank, rs.getString("account_number"), rs.getString("account_holder"));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }
}
