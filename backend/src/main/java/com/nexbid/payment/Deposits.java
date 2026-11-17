package com.nexbid.payment;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionLifecycleEvent;
import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionStatus;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.auction.LotDeposits;
import com.nexbid.auction.ReserveEvents;
import com.nexbid.common.BankReferences;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.user.BankAccount;
import com.nexbid.user.BankAccounts;
import com.nexbid.user.UserService;

/**
 * EN: Deposits for expensive lots (see {@link LotDeposits} for which and how much). A bidder pays through VNPay; the
 *     money is held while it can still decide the sale. Those who do not win are owed it back when the lot closes, the
 *     winner once they have paid; a winner who lets the payment lapse forfeits it. NexBid returns money by bank
 *     transfer, so "owed" means an admin's queue, the same way stray charges are handled — which is why a refund
 *     account is needed before paying.
 * VI: Tiền cọc cho lô đắt (xem {@link LotDeposits} để biết lô nào và bao nhiêu). Người trả giá đóng qua VNPay; tiền được
 *     giữ khi nó còn có thể quyết định giao dịch. Người không thắng được trả lại khi lô đóng, người thắng sau khi đã thanh
 *     toán; người thắng để quá hạn thanh toán thì mất cọc. NexBid trả tiền bằng chuyển khoản, nên "được trả lại" nghĩa là
 *     vào hàng chờ của admin, giống cách xử lý khoản thu thừa — vì vậy cần có tài khoản nhận hoàn tiền trước khi đóng cọc.
 */
@Service
public class Deposits {

    private static final Logger log = LoggerFactory.getLogger(Deposits.class);
    /** EN: Marks a VNPay reference as a deposit's. / VI: Đánh dấu mã giao dịch VNPay là của khoản cọc. */
    static final String PREFIX = "D";

    private final JdbcTemplate jdbc;
    private final LotDeposits rules;
    private final AuctionService auctions;
    private final BankAccounts bankAccounts;
    private final BankReferences references;
    private final UserService users;
    private final VnpayProperties properties;
    private final VnpaySigner signer;
    private final ApplicationEventPublisher events;

    Deposits(JdbcTemplate jdbc, LotDeposits rules, AuctionService auctions, BankAccounts bankAccounts,
            BankReferences references, UserService users, VnpayProperties properties,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.rules = rules;
        this.auctions = auctions;
        this.bankAccounts = bankAccounts;
        this.references = references;
        this.users = users;
        this.properties = properties;
        this.signer = new VnpaySigner(properties.hashSecret());
        this.events = events;
    }

    /** EN: A deposit, with the lot's title. / VI: Một khoản cọc, kèm tên lô. */
    public record DepositView(UUID id, UUID auctionId, String lotTitle, BigDecimal amount, String status,
            Instant createdAt, Instant paidAt, Instant settledAt, String reference) {
    }

    /** EN: An admin's row: whom to pay back and where. / VI: Một dòng của admin: trả lại cho ai và vào đâu. */
    public record DueView(DepositView deposit, UUID userId, String userName, BankAccount account) {
    }

    /** EN: What a lot asks and what the caller has: {@code amount} null when no deposit is needed. / VI: Lô yêu cầu gì và người gọi đã có gì: {@code amount} null khi không cần cọc. */
    public record LotStatus(BigDecimal amount, boolean held) {
    }

    private record Row(UUID id, UUID auctionId, UUID userId, BigDecimal amount, String txnRef, String status,
            Instant createdAt, Instant paidAt, Instant settledAt, String reference) {
    }

    public LotStatus statusOn(UUID auctionId, UUID userId) {
        BigDecimal amount = rules.amountFor(auctionId);
        return new LotStatus(amount, amount != null && userId != null && rules.holds(auctionId, userId));
    }

    /**
     * EN: Starts a VNPay checkout for the lot's deposit. Only while the lot is scheduled or open, only once per person,
     *     and only with a bank account to return it to.
     * VI: Bắt đầu checkout VNPay cho tiền cọc của lô. Chỉ khi lô đang chờ mở hoặc đang mở, mỗi người một lần, và chỉ khi
     *     có tài khoản ngân hàng để trả lại.
     */
    @Transactional
    public VnpayService.Checkout start(UUID userId, UUID auctionId, String clientIp, String locale) {
        BigDecimal amount = rules.amountFor(auctionId);
        if (amount == null) {
            throw new BusinessException(ErrorCode.DEPOSIT_NOT_NEEDED, "This lot does not ask for a deposit");
        }
        AuctionService.OfferTerms lot = auctions.offerTermsOf(auctionId);
        if (lot.sellerId().equals(userId)) {
            throw new BusinessException(ErrorCode.SELLER_CANNOT_BID, "You cannot bid on your own lot");
        }
        AuctionSummaryView summary = auctions.publicSummariesOf(List.of(auctionId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUCTION_NOT_FOUND, "No such lot"));
        AuctionStatus status = summary.auction().status();
        Instant now = Instant.now();
        if ((status != AuctionStatus.SCHEDULED && status != AuctionStatus.ACTIVE) || !lot.endTime().isAfter(now)) {
            throw new BusinessException(ErrorCode.AUCTION_NOT_ACTIVE, "Deposits are taken only before the lot closes");
        }
        if (rules.holds(auctionId, userId)) {
            throw new BusinessException(ErrorCode.DEPOSIT_ALREADY_HELD, "Your deposit for this lot is already held");
        }
        if (bankAccounts.of(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.REFUND_ACCOUNT_REQUIRED,
                    "Add a bank account for refunds first, so the deposit can be returned");
        }
        String txnRef = PREFIX + VnpayService.newTxnRef(now);
        jdbc.update("""
                INSERT INTO lot_deposits (id, auction_id, user_id, amount, txn_ref, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?)
                """, UUID.randomUUID(), auctionId, userId, amount, txnRef, utc(now));
        Instant expires = now.plus(properties.timeout());
        if (lot.endTime().isBefore(expires)) {
            expires = lot.endTime();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", properties.tmnCode());
        fields.put("vnp_Amount", VnpayService.minorUnits(amount));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", txnRef);
        fields.put("vnp_OrderInfo", "NexBid deposit " + auctionId);
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vi".equals(locale) ? "vn" : "en");
        fields.put("vnp_ReturnUrl", properties.returnUrl());
        fields.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        fields.put("vnp_CreateDate", VnpayService.STAMP.format(now));
        fields.put("vnp_ExpireDate", VnpayService.STAMP.format(expires));
        return new VnpayService.Checkout(properties.payUrl() + "?" + signer.signedQuery(fields), txnRef);
    }

    /**
     * EN: Applies VNPay's report for a deposit attempt; the signature was checked by the caller. Idempotent like
     *     payments. A second success for a lot where one is already held is owed straight back.
     * VI: Áp lời báo của VNPay cho một lần đóng cọc; chữ ký đã được bên gọi kiểm. Idempotent như khoản thanh toán. Lần
     *     thành công thứ hai cho lô đã có cọc được giữ sẽ được trả lại ngay.
     */
    @Transactional
    VnpayService.Confirmation confirm(Map<String, String> fields) {
        String txnRef = fields.get("vnp_TxnRef");
        Row attempt = jdbc.query("SELECT * FROM lot_deposits WHERE txn_ref = ? FOR UPDATE", (rs, n) -> row(rs), txnRef)
                .stream().findFirst().orElse(null);
        if (attempt == null) {
            return new VnpayService.Confirmation(VnpayService.Outcome.NOT_FOUND, null, false, null);
        }
        if (!VnpayService.minorUnits(attempt.amount()).equals(fields.get("vnp_Amount"))) {
            return new VnpayService.Confirmation(VnpayService.Outcome.INVALID_AMOUNT, attempt.id(), false, null);
        }
        if (!"PENDING".equals(attempt.status())) {
            return new VnpayService.Confirmation(VnpayService.Outcome.ALREADY_CONFIRMED, attempt.id(),
                    !"FAILED".equals(attempt.status()), attempt.status());
        }
        String code = fields.get("vnp_ResponseCode");
        boolean success = VnpayService.APPROVED.equals(code)
                && VnpayService.APPROVED.equals(fields.getOrDefault("vnp_TransactionStatus", code));
        Instant now = Instant.now();
        String next = !success ? "FAILED" : rules.holds(attempt.auctionId(), attempt.userId()) ? "REFUND_DUE" : "PAID";
        jdbc.update("""
                UPDATE lot_deposits SET status = ?, response_code = ?, transaction_no = ?, paid_at = ? WHERE id = ?
                """, next, code, fields.get("vnp_TransactionNo"), success ? utc(now) : null, attempt.id());
        if ("REFUND_DUE".equals(next)) {
            log.warn("Deposit {} paid twice for lot {}; queued for a refund", txnRef, attempt.auctionId());
        }
        return new VnpayService.Confirmation(VnpayService.Outcome.CONFIRMED, attempt.id(), success, code);
    }

    public DepositView view(UUID depositId) {
        return views(jdbc.query("SELECT * FROM lot_deposits WHERE id = ?", (rs, n) -> row(rs), depositId)).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DEPOSIT_NOT_FOUND, "No deposit " + depositId));
    }

    /** EN: The person's deposits, newest first, failed attempts left out. / VI: Các khoản cọc của người đó, mới nhất trước, bỏ các lần thất bại. */
    public List<DepositView> mine(UUID userId) {
        return views(jdbc.query("""
                SELECT * FROM lot_deposits WHERE user_id = ? AND status NOT IN ('PENDING', 'FAILED')
                ORDER BY created_at DESC LIMIT 100
                """, (rs, n) -> row(rs), userId));
    }

    /** EN: Deposits an admin must transfer back, oldest first. / VI: Các khoản cọc admin cần chuyển trả, cũ nhất trước. */
    public List<DueView> due() {
        List<Row> rows = jdbc.query("SELECT * FROM lot_deposits WHERE status = 'REFUND_DUE' ORDER BY paid_at LIMIT 200",
                (rs, n) -> row(rs));
        List<DepositView> views = views(rows);
        Map<UUID, String> names = users.namesOf(rows.stream().map(Row::userId).distinct().toList());
        return java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(i -> new DueView(views.get(i), rows.get(i).userId(), names.get(rows.get(i).userId()),
                        bankAccounts.of(rows.get(i).userId()).orElse(null)))
                .toList();
    }

    /** EN: Records the transfer that returned a deposit. / VI: Ghi nhận lần chuyển khoản trả lại một khoản cọc. */
    @Transactional
    public DepositView refunded(UUID adminId, UUID depositId, String reference) {
        Row deposit = jdbc.query("SELECT * FROM lot_deposits WHERE id = ? FOR UPDATE", (rs, n) -> row(rs), depositId)
                .stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DEPOSIT_NOT_FOUND, "No deposit " + depositId));
        if (!"REFUND_DUE".equals(deposit.status())) {
            throw new BusinessException(ErrorCode.DEPOSIT_NOT_DUE, "This deposit is not waiting to be returned");
        }
        BankAccount account = bankAccounts.of(deposit.userId()).orElseThrow(() -> new BusinessException(
                ErrorCode.REFUND_ACCOUNT_REQUIRED, "The bidder has not added a bank account for refunds yet"));
        String ref = reference.trim();
        references.claim(ref, "DEPOSIT");
        Instant now = Instant.now();
        jdbc.update("""
                UPDATE lot_deposits SET status = 'REFUNDED', reference = ?, account = ?, settled_by = ?, settled_at = ?
                WHERE id = ?
                """, ref, account.masked(), adminId, utc(now), depositId);
        events.publishEvent(new DepositEvents.Settled(depositId, deposit.auctionId(), deposit.userId(),
                deposit.amount(), "REFUNDED", adminId, now));
        return view(depositId);
    }

    // ---- what happens to held deposits as the lot moves on / những gì xảy ra với tiền cọc khi lô tiếp diễn

    /**
     * EN: A lot closed: everyone but the leading bidder (the winner, or the one a reserve decision waits on) is owed
     *     their deposit. Removed: everyone is.
     * VI: Lô đóng: mọi người trừ người dẫn đầu (người thắng, hoặc người mà quyết định giá sàn đang chờ) được trả lại cọc.
     *     Bị gỡ: tất cả được trả lại.
     */
    @EventListener
    void onLifecycle(AuctionLifecycleEvent event) {
        if (AuctionLifecycleEvent.ENDED.equals(event.type())) {
            UUID keep = event.winnerId() != null ? event.winnerId() : event.leaderId();
            release(event.auctionId(), keep);
        } else if (AuctionLifecycleEvent.REMOVED.equals(event.type())) {
            release(event.auctionId(), null);
        }
    }

    @EventListener
    void onReserveRefused(ReserveEvents.Refused event) {
        releaseOne(event.auctionId(), event.leaderId());
    }

    @EventListener
    void onPaid(PaymentEvents.Succeeded event) {
        releaseOne(event.auctionId(), event.userId());
    }

    /** EN: The winner never paid: the deposit is kept. / VI: Người thắng không bao giờ trả tiền: tiền cọc bị giữ lại. */
    @EventListener
    void onExpired(PaymentEvents.Expired event) {
        Instant now = Instant.now();
        jdbc.query("""
                UPDATE lot_deposits SET status = 'FORFEITED', settled_at = ?
                WHERE auction_id = ? AND user_id = ? AND status = 'PAID' RETURNING id, amount
                """, (rs, n) -> {
            events.publishEvent(new DepositEvents.Settled(rs.getObject(1, UUID.class), event.auctionId(),
                    event.userId(), rs.getBigDecimal(2), "FORFEITED", null, now));
            return null;
        }, utc(now), event.auctionId(), event.userId());
    }

    private void release(UUID auctionId, UUID keep) {
        if (keep == null) {
            jdbc.update("UPDATE lot_deposits SET status = 'REFUND_DUE' WHERE auction_id = ? AND status = 'PAID'",
                    auctionId);
        } else {
            jdbc.update("""
                    UPDATE lot_deposits SET status = 'REFUND_DUE' WHERE auction_id = ? AND status = 'PAID' AND user_id <> ?
                    """, auctionId, keep);
        }
    }

    private void releaseOne(UUID auctionId, UUID userId) {
        if (userId != null) {
            jdbc.update("""
                    UPDATE lot_deposits SET status = 'REFUND_DUE' WHERE auction_id = ? AND user_id = ? AND status = 'PAID'
                    """, auctionId, userId);
        }
    }

    private List<DepositView> views(List<Row> rows) {
        Map<UUID, String> titles = new java.util.HashMap<>();
        rows.stream().map(Row::auctionId).distinct().forEach(id -> titles.put(id, auctions.lotTitleOf(id)));
        return rows.stream().map(row -> new DepositView(row.id(), row.auctionId(), titles.get(row.auctionId()),
                row.amount(), row.status(), row.createdAt(), row.paidAt(), row.settledAt(), row.reference())).toList();
    }

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("auction_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getBigDecimal("amount"), rs.getString("txn_ref"),
                rs.getString("status"), instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("paid_at")),
                instant(rs.getTimestamp("settled_at")), rs.getString("reference"));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Object utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }
}
