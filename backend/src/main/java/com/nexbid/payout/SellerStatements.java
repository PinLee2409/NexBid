package com.nexbid.payout;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * EN: A seller's statement for one month (Vietnam time), for their own books and tax return: each sale completed that
 *     month with the price, the platform fee, the shipping, what they are owed, and whether it was paid out; plus the
 *     bank transfers NexBid made to them that month.
 * VI: Bảng kê của người bán cho một tháng (giờ Việt Nam), để họ ghi sổ và kê khai thuế: mỗi giao dịch hoàn tất trong
 *     tháng với giá bán, phí sàn, phí ship, số được nhận, và đã chi hay chưa; cùng các lần NexBid chuyển khoản cho họ
 *     trong tháng.
 */
@Service
public class SellerStatements {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;

    SellerStatements(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Line(Instant at, UUID orderId, String lot, BigDecimal gross, BigDecimal fee, BigDecimal shipping,
            BigDecimal net, boolean paidOut) {
    }

    public record Transfer(Instant at, BigDecimal amount, String reference) {
    }

    public record Statement(YearMonth month, String seller, List<Line> lines, BigDecimal gross, BigDecimal fees,
            BigDecimal shipping, BigDecimal net, List<Transfer> transfers, BigDecimal transferred) {
    }

    public Statement of(UUID sellerId, YearMonth month) {
        Instant from = month.atDay(1).atStartOfDay(VIETNAM).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(VIETNAM).toInstant();
        List<Line> lines = jdbc.query("""
                SELECT e.created_at, e.order_id, p.name, e.gross, e.fee, e.shipping, e.net, e.payout_id IS NOT NULL AS paid
                FROM seller_earnings e
                JOIN auctions a ON a.id = e.auction_id
                JOIN products p ON p.id = a.product_id
                WHERE e.seller_id = ? AND e.created_at >= ? AND e.created_at < ?
                ORDER BY e.created_at
                """, (rs, n) -> new Line(rs.getTimestamp(1).toInstant(), rs.getObject(2, UUID.class), rs.getString(3),
                rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBoolean(8)),
                sellerId, java.sql.Timestamp.from(from), java.sql.Timestamp.from(to));
        List<Transfer> transfers = jdbc.query("""
                SELECT created_at, amount, reference FROM seller_payouts
                WHERE seller_id = ? AND created_at >= ? AND created_at < ? ORDER BY created_at
                """, (rs, n) -> new Transfer(rs.getTimestamp(1).toInstant(), rs.getBigDecimal(2), rs.getString(3)),
                sellerId, java.sql.Timestamp.from(from), java.sql.Timestamp.from(to));
        String seller = jdbc.queryForObject("SELECT full_name FROM users WHERE id = ?", String.class, sellerId);
        return new Statement(month, seller, lines,
                sum(lines.stream().map(Line::gross).toList()), sum(lines.stream().map(Line::fee).toList()),
                sum(lines.stream().map(Line::shipping).toList()), sum(lines.stream().map(Line::net).toList()),
                transfers, sum(transfers.stream().map(Transfer::amount).toList()));
    }

    /** EN: The same, as spreadsheet rows; dates in Vietnam time. / VI: Như trên, dạng dòng bảng tính; ngày theo giờ Việt Nam. */
    public static List<List<String>> rows(Statement statement) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("date", "order", "lot", "sale_price", "platform_fee", "shipping", "owed_to_you", "paid_out"));
        for (Line line : statement.lines()) {
            rows.add(List.of(date(line.at()), line.orderId().toString(), line.lot(), line.gross().toPlainString(),
                    line.fee().toPlainString(), line.shipping().toPlainString(), line.net().toPlainString(),
                    line.paidOut() ? "yes" : "no"));
        }
        rows.add(List.of("total", "", "", statement.gross().toPlainString(), statement.fees().toPlainString(),
                statement.shipping().toPlainString(), statement.net().toPlainString(), ""));
        rows.add(List.of());
        rows.add(List.of("transfer_date", "reference", "amount"));
        for (Transfer transfer : statement.transfers()) {
            rows.add(List.of(date(transfer.at()), transfer.reference(), transfer.amount().toPlainString()));
        }
        rows.add(List.of("transferred", "", statement.transferred().toPlainString()));
        return rows;
    }

    private static String date(Instant at) {
        return LocalDate.ofInstant(at, VIETNAM).toString();
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
