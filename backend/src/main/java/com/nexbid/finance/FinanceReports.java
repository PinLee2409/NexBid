package com.nexbid.finance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;

/**
 * EN: Reconciliation and revenue, in Vietnam days and months. Amounts are VND.
 * VI: Đối soát và doanh thu, theo ngày và tháng giờ Việt Nam. Số tiền tính bằng VND.
 */
@Service
public class FinanceReports {

    static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int MAX_DAYS = 366;

    private final JdbcTemplate jdbc;

    FinanceReports(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * EN: One day's money. {@code charged}: VNPay attempts that succeeded. {@code applied}: those that paid a lot.
     *     {@code stray}: charged but owed back (late or duplicate). {@code refunded}, {@code strayRefunded},
     *     {@code paidOut}: bank transfers admins recorded. {@code unmatched}: successful charges neither applied nor
     *     queued as stray — it should always be zero.
     * VI: Tiền của một ngày. {@code charged}: các lần thử VNPay thành công. {@code applied}: những lần đã trả cho một
     *     lô. {@code stray}: đã thu nhưng phải trả lại (trễ hoặc trùng). {@code refunded}, {@code strayRefunded},
     *     {@code paidOut}: các lần chuyển khoản admin đã ghi. {@code unmatched}: lần thu thành công mà không áp vào lô
     *     nào cũng không nằm trong hàng chờ trả lại — luôn phải bằng không.
     */
    public record Day(LocalDate day, int charges, BigDecimal charged, BigDecimal applied, BigDecimal stray,
            BigDecimal refunded, BigDecimal strayRefunded, BigDecimal paidOut, int unmatched) {
    }

    public record Reconciliation(LocalDate from, LocalDate to, List<Day> days, Day total) {
    }

    @Transactional(readOnly = true)
    public Reconciliation reconcile(LocalDate from, LocalDate to) {
        requireRange(from, to);
        OffsetDateTime start = from.atStartOfDay(VIETNAM).toOffsetDateTime();
        OffsetDateTime end = to.plusDays(1).atStartOfDay(VIETNAM).toOffsetDateTime();
        List<Day> days = jdbc.query("""
                WITH days AS (
                    SELECT generate_series(?::date, ?::date, interval '1 day')::date AS day
                ), charge AS (
                    SELECT (t.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day, COUNT(*) AS n,
                           SUM(t.amount) AS amount,
                           SUM(CASE WHEN s.id IS NULL AND p.status IN ('SUCCESS', 'REFUNDED') THEN t.amount ELSE 0 END)
                               AS applied,
                           SUM(CASE WHEN s.id IS NOT NULL THEN t.amount ELSE 0 END) AS stray,
                           COUNT(*) FILTER (WHERE s.id IS NULL AND p.status NOT IN ('SUCCESS', 'REFUNDED')) AS unmatched
                    FROM payment_transactions t
                    JOIN payments p ON p.id = t.payment_id
                    LEFT JOIN stray_charges s ON s.txn_ref = t.txn_ref
                    WHERE t.status = 'SUCCESS' AND t.updated_at >= ? AND t.updated_at < ?
                    GROUP BY 1
                ), refund AS (
                    SELECT (created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day, SUM(amount) AS amount
                    FROM payment_refunds WHERE created_at >= ? AND created_at < ? GROUP BY 1
                ), stray_back AS (
                    SELECT (refunded_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day, SUM(amount) AS amount
                    FROM stray_charges WHERE refunded_at >= ? AND refunded_at < ? GROUP BY 1
                ), payout AS (
                    SELECT (created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day, SUM(amount) AS amount
                    FROM seller_payouts WHERE created_at >= ? AND created_at < ? GROUP BY 1
                )
                SELECT d.day, COALESCE(c.n, 0) AS charges, COALESCE(c.amount, 0) AS charged,
                       COALESCE(c.applied, 0) AS applied, COALESCE(c.stray, 0) AS stray,
                       COALESCE(r.amount, 0) AS refunded, COALESCE(sb.amount, 0) AS stray_refunded,
                       COALESCE(po.amount, 0) AS paid_out, COALESCE(c.unmatched, 0) AS unmatched
                FROM days d
                LEFT JOIN charge c ON c.day = d.day
                LEFT JOIN refund r ON r.day = d.day
                LEFT JOIN stray_back sb ON sb.day = d.day
                LEFT JOIN payout po ON po.day = d.day
                ORDER BY d.day
                """, (rs, n) -> new Day(rs.getObject("day", LocalDate.class), rs.getInt("charges"),
                rs.getBigDecimal("charged"), rs.getBigDecimal("applied"), rs.getBigDecimal("stray"),
                rs.getBigDecimal("refunded"), rs.getBigDecimal("stray_refunded"), rs.getBigDecimal("paid_out"),
                rs.getInt("unmatched")),
                from, to, start, end, start, end, start, end, start, end);
        return new Reconciliation(from, to, days, sum(days));
    }

    /** EN: One VNPay charge, for the CSV an admin compares with VNPay's own report. / VI: Một lần thu của VNPay, cho file CSV admin so với báo cáo của VNPay. */
    public record Charge(Instant at, String txnRef, String transactionNo, String bank, BigDecimal amount,
            String outcome, UUID paymentId, String lot, String buyer) {
    }

    @Transactional(readOnly = true)
    public List<Charge> charges(LocalDate from, LocalDate to) {
        requireRange(from, to);
        return jdbc.query("""
                SELECT t.updated_at, t.txn_ref, t.transaction_no, t.bank_code, t.amount, p.id AS payment_id,
                       CASE WHEN s.id IS NOT NULL THEN 'STRAY_' || s.reason
                            WHEN p.status = 'REFUNDED' THEN 'REFUNDED' ELSE 'APPLIED' END AS outcome,
                       pr.name AS lot, u.email AS buyer
                FROM payment_transactions t
                JOIN payments p ON p.id = t.payment_id
                JOIN auctions a ON a.id = p.auction_id
                JOIN products pr ON pr.id = a.product_id
                JOIN users u ON u.id = p.user_id
                LEFT JOIN stray_charges s ON s.txn_ref = t.txn_ref
                WHERE t.status = 'SUCCESS' AND t.updated_at >= ? AND t.updated_at < ?
                ORDER BY t.updated_at
                """, (rs, n) -> new Charge(rs.getTimestamp("updated_at").toInstant(), rs.getString("txn_ref"),
                rs.getString("transaction_no"), rs.getString("bank_code"), rs.getBigDecimal("amount"),
                rs.getString("outcome"), rs.getObject("payment_id", UUID.class), rs.getString("lot"),
                rs.getString("buyer")),
                from.atStartOfDay(VIETNAM).toOffsetDateTime(), to.plusDays(1).atStartOfDay(VIETNAM).toOffsetDateTime());
    }

    /**
     * EN: Completed sales by the month they completed: goods sold, the platform's fee, shipping passed to sellers.
     * VI: Giao dịch hoàn tất theo tháng hoàn tất: tiền hàng đã bán, phí sàn, phí ship chuyển cho người bán.
     */
    public record Month(YearMonth month, int sales, BigDecimal gross, BigDecimal fees, BigDecimal shipping) {
    }

    public record Share(String name, int sales, BigDecimal gross, BigDecimal fees) {
    }

    public record Revenue(List<Month> months, List<Share> categories, List<Share> sellers) {
    }

    @Transactional(readOnly = true)
    public Revenue revenue(int monthsBack, Instant now) {
        int months = Math.max(1, Math.min(monthsBack, 36));
        LocalDate first = YearMonth.from(now.atZone(VIETNAM)).minusMonths(months - 1L).atDay(1);
        OffsetDateTime since = first.atStartOfDay(VIETNAM).toOffsetDateTime();
        List<Month> byMonth = jdbc.query("""
                WITH months AS (
                    SELECT generate_series(?::date, date_trunc('month', now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
                                           interval '1 month')::date AS month
                ), sold AS (
                    SELECT date_trunc('month', created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS month,
                           COUNT(*) AS sales, SUM(gross) AS gross, SUM(fee) AS fees, SUM(shipping) AS shipping
                    FROM seller_earnings WHERE created_at >= ? GROUP BY 1
                )
                SELECT m.month, COALESCE(s.sales, 0) AS sales, COALESCE(s.gross, 0) AS gross,
                       COALESCE(s.fees, 0) AS fees, COALESCE(s.shipping, 0) AS shipping
                FROM months m LEFT JOIN sold s ON s.month = m.month ORDER BY m.month
                """, (rs, n) -> new Month(YearMonth.from(rs.getObject("month", LocalDate.class)), rs.getInt("sales"),
                rs.getBigDecimal("gross"), rs.getBigDecimal("fees"), rs.getBigDecimal("shipping")), first, since);
        List<Share> byCategory = jdbc.query("""
                SELECT c.name, COUNT(*) AS sales, SUM(e.gross) AS gross, SUM(e.fee) AS fees
                FROM seller_earnings e
                JOIN auctions a ON a.id = e.auction_id
                JOIN products p ON p.id = a.product_id
                JOIN categories c ON c.id = p.category_id
                WHERE e.created_at >= ?
                GROUP BY c.name ORDER BY SUM(e.gross) DESC
                """, (rs, n) -> share(rs), since);
        List<Share> bySeller = jdbc.query("""
                SELECT u.full_name AS name, COUNT(*) AS sales, SUM(e.gross) AS gross, SUM(e.fee) AS fees
                FROM seller_earnings e JOIN users u ON u.id = e.seller_id
                WHERE e.created_at >= ?
                GROUP BY u.id, u.full_name ORDER BY SUM(e.gross) DESC LIMIT 20
                """, (rs, n) -> share(rs), since);
        return new Revenue(byMonth, byCategory, bySeller);
    }

    private static Share share(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Share(rs.getString("name"), rs.getInt("sales"), rs.getBigDecimal("gross"), rs.getBigDecimal("fees"));
    }

    private static Day sum(List<Day> days) {
        BigDecimal[] totals = new BigDecimal[7];
        java.util.Arrays.fill(totals, BigDecimal.ZERO);
        int charges = 0;
        int unmatched = 0;
        for (Day day : days) {
            charges += day.charges();
            unmatched += day.unmatched();
            List<BigDecimal> values = List.of(day.charged(), day.applied(), day.stray(), day.refunded(),
                    day.strayRefunded(), day.paidOut());
            for (int i = 0; i < values.size(); i++) {
                totals[i] = totals[i].add(values.get(i));
            }
        }
        return new Day(null, charges, totals[0], totals[1], totals[2], totals[3], totals[4], totals[5], unmatched);
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusDays(MAX_DAYS).isBefore(to)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Choose a range of at most a year, start first");
        }
    }

    /** EN: Rows for a CSV file. / VI: Các dòng cho file CSV. */
    static List<List<String>> chargeRows(List<Charge> charges) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("time_vn", "txn_ref", "vnpay_transaction_no", "bank", "amount", "outcome", "payment_id", "lot",
                "buyer"));
        for (Charge charge : charges) {
            rows.add(List.of(charge.at().atZone(VIETNAM).toLocalDateTime().toString(), charge.txnRef(),
                    String.valueOf(charge.transactionNo()), String.valueOf(charge.bank()),
                    charge.amount().toPlainString(), charge.outcome(), charge.paymentId().toString(), charge.lot(),
                    charge.buyer()));
        }
        return rows;
    }

    static List<List<String>> revenueRows(Revenue revenue) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("month", "sales", "gross", "platform_fees", "shipping_to_sellers"));
        for (Month month : revenue.months()) {
            rows.add(List.of(month.month().toString(), String.valueOf(month.sales()), month.gross().toPlainString(),
                    month.fees().toPlainString(), month.shipping().toPlainString()));
        }
        return rows;
    }
}
