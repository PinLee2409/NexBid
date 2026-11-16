package com.nexbid.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;

/**
 * EN: Which lots ask for a deposit, how much, and whether a person has one held. A lot starting at or above the
 *     threshold asks for a share of its starting price, kept between a floor and a ceiling. The bid module checks this
 *     before every bid, auto bid, buy-now and offer; the payment module takes the money through VNPay.
 * VI: Lô nào cần đặt cọc, bao nhiêu, và một người đã có tiền cọc đang được giữ chưa. Lô có giá khởi điểm từ ngưỡng trở
 *     lên yêu cầu một phần giá khởi điểm, giữ trong khoảng sàn và trần. Module bid kiểm điều này trước mọi lượt trả giá,
 *     auto bid, mua ngay và đề nghị; module payment thu tiền qua VNPay.
 */
@Service
public class LotDeposits {

    private static final BigDecimal ROUND = BigDecimal.valueOf(10_000);

    private final JdbcTemplate jdbc;
    private final BigDecimal threshold;
    private final BigDecimal percent;
    private final BigDecimal floor;
    private final BigDecimal ceiling;

    LotDeposits(JdbcTemplate jdbc,
            @Value("${nexbid.deposit.threshold:50000000}") BigDecimal threshold,
            @Value("${nexbid.deposit.percent:10}") BigDecimal percent,
            @Value("${nexbid.deposit.min:500000}") BigDecimal floor,
            @Value("${nexbid.deposit.max:10000000}") BigDecimal ceiling) {
        this.jdbc = jdbc;
        this.threshold = threshold;
        this.percent = percent;
        this.floor = floor;
        this.ceiling = ceiling;
    }

    /**
     * EN: The deposit for a lot starting at this price, or null when none is needed. A threshold of zero turns
     *     deposits off.
     * VI: Tiền cọc cho lô có giá khởi điểm này, hoặc null khi không cần. Ngưỡng bằng 0 là tắt đặt cọc.
     */
    public BigDecimal amountFor(BigDecimal startingPrice) {
        if (threshold.signum() <= 0 || startingPrice == null || startingPrice.compareTo(threshold) < 0) {
            return null;
        }
        BigDecimal share = startingPrice.multiply(percent).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .divide(ROUND, 0, RoundingMode.DOWN).multiply(ROUND);
        return share.max(floor).min(ceiling);
    }

    /** EN: The deposit a lot asks for, or null. / VI: Tiền cọc một lô yêu cầu, hoặc null. */
    public BigDecimal amountFor(UUID auctionId) {
        return amountFor(jdbc.query("SELECT starting_price FROM auctions WHERE id = ?",
                (rs, n) -> rs.getBigDecimal(1), auctionId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AUCTION_NOT_FOUND,
                        "No auction with id " + auctionId)));
    }

    /** EN: Whether the person has a deposit held on the lot. / VI: Người đó có tiền cọc đang được giữ trên lô không. */
    public boolean holds(UUID auctionId, UUID userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM lot_deposits WHERE auction_id = ? AND user_id = ? AND status = 'PAID')",
                Boolean.class, auctionId, userId));
    }

    /**
     * EN: Stops a bid, auto bid, buy-now or offer on a lot that asks for a deposit the person has not paid.
     * VI: Chặn lượt trả giá, auto bid, mua ngay hoặc đề nghị trên lô yêu cầu tiền cọc mà người đó chưa đóng.
     */
    public void require(UUID auctionId, UUID userId) {
        BigDecimal amount = amountFor(auctionId);
        if (amount != null && !holds(auctionId, userId)) {
            throw new BusinessException(ErrorCode.DEPOSIT_REQUIRED,
                    "This lot asks for a deposit of " + amount.toPlainString() + " VND before you can bid");
        }
    }
}
