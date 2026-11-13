package com.nexbid.payout;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.order.OrderEvents;
import com.nexbid.user.BankAccount;
import com.nexbid.user.BankAccounts;
import com.nexbid.user.UserAccount;
import com.nexbid.user.UserService;

/**
 * EN: What the platform owes sellers and what it has paid them. Buyers pay the platform; each completed order then
 *     owes its seller the price less the platform's fee, and an admin settles a seller's whole balance with one bank
 *     transfer, recorded here with its reference. A refund can only happen before completion, so nothing owed is ever
 *     taken back.
 * VI: Sàn nợ người bán bao nhiêu và đã trả họ những gì. Người mua trả tiền cho sàn; mỗi đơn hoàn tất khiến sàn nợ người
 *     bán giá bán trừ phí sàn, và admin trả toàn bộ số dư của một người bán bằng một lần chuyển khoản, được ghi lại ở
 *     đây kèm mã giao dịch. Hoàn tiền chỉ xảy ra trước khi hoàn tất, nên khoản đã nợ không bao giờ bị lấy lại.
 */
@Service
public class PayoutService {

    private final JdbcTemplate jdbc;
    private final AuctionService auctions;
    private final UserService users;
    private final ApplicationEventPublisher events;
    private final BigDecimal feePercent;
    private final BankAccounts bankAccounts;

    PayoutService(JdbcTemplate jdbc, AuctionService auctions, UserService users, ApplicationEventPublisher events,
            @Value("${nexbid.payout.fee-percent}") BigDecimal feePercent, BankAccounts bankAccounts) {
        this.jdbc = jdbc;
        this.auctions = auctions;
        this.users = users;
        this.events = events;
        this.feePercent = feePercent;
        this.bankAccounts = bankAccounts;
    }

    /** EN: One completed sale and what the seller gets from it. / VI: Một giao dịch hoàn tất và người bán nhận được bao nhiêu. */
    public record Earning(UUID id, UUID orderId, UUID auctionId, BigDecimal gross, BigDecimal fee, BigDecimal net,
            UUID payoutId, Instant createdAt, AuctionSummaryView auction) {
    }

    /** EN: One transfer to a seller. / VI: Một lần chuyển tiền cho người bán. */
    public record Payout(UUID id, UUID sellerId, BigDecimal amount, BankAccount account, String reference,
            Instant createdAt) {
    }

    /** EN: The seller's page: what is owed, what was paid, and where it goes. / VI: Trang của người bán: còn được nhận bao nhiêu, đã nhận gì, và chuyển về đâu. */
    public record SellerEarnings(BigDecimal balance, BigDecimal paidOut, BigDecimal feePercent, BankAccount account,
            List<Earning> earnings, List<Payout> payouts) {
    }

    /** EN: A seller with money waiting, for the admin's queue. / VI: Một người bán đang có tiền chờ, cho hàng chờ của admin. */
    public record PayoutDue(UUID sellerId, String sellerName, String sellerEmail, BigDecimal balance, int orders,
            Instant oldest, BankAccount account) {
    }

    /**
     * EN: A completed order becomes money owed. Idempotent: the order id is unique, so a repeated event adds nothing.
     * VI: Một đơn hoàn tất trở thành khoản nợ người bán. Idempotent: order id là duy nhất, nên sự kiện lặp lại không thêm gì.
     */
    @EventListener
    void onCompleted(OrderEvents.Completed event) {
        BigDecimal fee = event.amount().multiply(feePercent).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        jdbc.update("""
                INSERT INTO seller_earnings (id, order_id, seller_id, auction_id, gross, fee, net, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (order_id) DO NOTHING
                """, UUID.randomUUID(), event.orderId(), event.sellerId(), event.auctionId(), event.amount(), fee,
                event.amount().subtract(fee), utc(event.at()));
    }

    @Transactional(readOnly = true)
    public SellerEarnings earningsOf(UUID sellerId) {
        List<Earning> earnings = jdbc.query("""
                SELECT * FROM seller_earnings WHERE seller_id = ? ORDER BY created_at DESC LIMIT 200
                """, (rs, n) -> earning(rs, null), sellerId);
        Map<UUID, AuctionSummaryView> lots = auctions.participantSummariesOf(
                earnings.stream().map(Earning::auctionId).distinct().toList());
        List<Earning> withLots = earnings.stream()
                .map(e -> new Earning(e.id(), e.orderId(), e.auctionId(), e.gross(), e.fee(), e.net(), e.payoutId(),
                        e.createdAt(), lots.get(e.auctionId())))
                .toList();

        BigDecimal balance = jdbc.queryForObject(
                "SELECT COALESCE(SUM(net), 0) FROM seller_earnings WHERE seller_id = ? AND payout_id IS NULL",
                BigDecimal.class, sellerId);
        BigDecimal paidOut = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM seller_payouts WHERE seller_id = ?", BigDecimal.class, sellerId);
        return new SellerEarnings(balance, paidOut, feePercent, accountOf(sellerId).orElse(null), withLots,
                payoutsOf(sellerId));
    }

    private Optional<BankAccount> accountOf(UUID sellerId) {
        return bankAccounts.of(sellerId);
    }

    /** EN: Sellers with money waiting, longest-waiting first. / VI: Người bán có tiền đang chờ, chờ lâu nhất trước. */
    @Transactional(readOnly = true)
    public List<PayoutDue> due() {
        record Owed(UUID sellerId, BigDecimal balance, int orders, Instant oldest) {
        }
        List<Owed> owed = jdbc.query("""
                SELECT seller_id, SUM(net) AS balance, COUNT(*) AS orders, MIN(created_at) AS oldest
                FROM seller_earnings WHERE payout_id IS NULL
                GROUP BY seller_id ORDER BY MIN(created_at)
                """, (rs, n) -> new Owed(rs.getObject("seller_id", UUID.class), rs.getBigDecimal("balance"),
                rs.getInt("orders"), rs.getTimestamp("oldest").toInstant()));
        return owed.stream().map(o -> {
            Optional<UserAccount> seller = users.findById(o.sellerId());
            return new PayoutDue(o.sellerId(), seller.map(UserAccount::fullName).orElse(""),
                    seller.map(UserAccount::email).orElse(""), o.balance(), o.orders(), o.oldest(),
                    accountOf(o.sellerId()).orElse(null));
        }).toList();
    }

    /** EN: The latest transfers to every seller. / VI: Các lần chuyển tiền gần nhất cho mọi người bán. */
    @Transactional(readOnly = true)
    public List<Payout> recentPayouts() {
        return jdbc.query("SELECT * FROM seller_payouts ORDER BY created_at DESC LIMIT 100", (rs, n) -> payout(rs));
    }

    /**
     * EN: The admin made the bank transfer: every unpaid earning of the seller is settled by it. The earnings are
     *     locked first, so two admins clicking at once cannot record the same money twice.
     * VI: Admin đã chuyển khoản: mọi khoản chưa trả của người bán được chốt bằng lần chuyển này. Các khoản được khoá
     *     trước, để hai admin bấm cùng lúc không ghi cùng một khoản tiền hai lần.
     */
    @Transactional
    public Payout payOut(UUID adminId, UUID sellerId, String reference) {
        List<UUID> unpaid = jdbc.queryForList(
                "SELECT id FROM seller_earnings WHERE seller_id = ? AND payout_id IS NULL FOR UPDATE",
                UUID.class, sellerId);
        if (unpaid.isEmpty()) {
            throw new BusinessException(ErrorCode.NOTHING_TO_PAY_OUT, "This seller has nothing waiting to be paid");
        }
        BankAccount account = accountOf(sellerId).orElseThrow(() -> new BusinessException(
                ErrorCode.BANK_ACCOUNT_REQUIRED, "The seller has not added a bank account yet"));
        BigDecimal amount = jdbc.queryForObject(
                "SELECT SUM(net) FROM seller_earnings WHERE seller_id = ? AND payout_id IS NULL",
                BigDecimal.class, sellerId);

        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO seller_payouts (id, seller_id, amount, bank_name, account_number, account_holder,
                                            reference, paid_by, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, sellerId, amount, account.bankName(), account.accountNumber(), account.accountHolder(),
                reference.trim(), adminId, utc(now));
        jdbc.update("UPDATE seller_earnings SET payout_id = ? WHERE seller_id = ? AND payout_id IS NULL", id, sellerId);
        events.publishEvent(new PayoutEvents.Paid(id, sellerId, amount, reference.trim(), adminId, now));
        return new Payout(id, sellerId, amount, account, reference.trim(), now);
    }

    private List<Payout> payoutsOf(UUID sellerId) {
        return jdbc.query("SELECT * FROM seller_payouts WHERE seller_id = ? ORDER BY created_at DESC LIMIT 100",
                (rs, n) -> payout(rs), sellerId);
    }

    private static Earning earning(ResultSet rs, AuctionSummaryView lot) throws SQLException {
        return new Earning(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getObject("auction_id", UUID.class), rs.getBigDecimal("gross"), rs.getBigDecimal("fee"),
                rs.getBigDecimal("net"), rs.getObject("payout_id", UUID.class),
                rs.getTimestamp("created_at").toInstant(), lot);
    }

    private static Payout payout(ResultSet rs) throws SQLException {
        return new Payout(rs.getObject("id", UUID.class), rs.getObject("seller_id", UUID.class),
                rs.getBigDecimal("amount"), account(rs), rs.getString("reference"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static BankAccount account(ResultSet rs) throws SQLException {
        return new BankAccount(rs.getString("bank_name"), rs.getString("account_number"),
                rs.getString("account_holder"));
    }

    private static OffsetDateTime utc(Instant at) {
        return at.atOffset(ZoneOffset.UTC);
    }
}
