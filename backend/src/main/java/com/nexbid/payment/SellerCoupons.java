package com.nexbid.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;

/**
 * EN: A seller's own discount codes, off the goods on their lots only. A percentage (1–50%) or a fixed amount, with an
 *     optional minimum price, a use limit and an end date; a code can be kept for the people who follow the seller.
 *     The seller bears the discount: their earnings are the price less the discount. Admin vouchers ({@link PromoCodes})
 *     stay what they were: NexBid paying part of the shipping.
 * VI: Mã giảm giá riêng của người bán, chỉ trừ vào tiền hàng trên lô của họ. Phần trăm (1–50%) hoặc số tiền cố định,
 *     có thể kèm giá tối thiểu, giới hạn số lượt và ngày hết hạn; mã có thể chỉ dành cho người theo dõi người bán.
 *     Người bán chịu khoản giảm: thu nhập của họ là giá trừ khoản giảm. Voucher của admin ({@link PromoCodes}) giữ như
 *     cũ: NexBid trả một phần phí ship.
 */
@Service
public class SellerCoupons {

    static final int LIMIT = 30;
    private static final BigDecimal ROUND = BigDecimal.valueOf(1_000);

    private final JdbcTemplate jdbc;

    SellerCoupons(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Coupon(UUID id, String code, Integer percentOff, BigDecimal amountOff, BigDecimal minPrice,
            boolean followersOnly, int maxUses, int used, Instant expiresAt, boolean active, Instant createdAt) {
    }

    record Valid(String code, BigDecimal discount) {
    }

    @Transactional
    public Coupon create(UUID sellerId, String code, Integer percentOff, BigDecimal amountOff, BigDecimal minPrice,
            boolean followersOnly, int maxUses, Instant expiresAt, Instant now) {
        String clean = PromoCodes.normalise(code);
        if (!clean.matches("[A-Z0-9-]{4,32}")) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "Use 4 to 32 letters, digits or dashes");
        }
        if ((percentOff == null) == (amountOff == null)) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "Give either a percentage or an amount");
        }
        if ((percentOff != null && (percentOff < 1 || percentOff > 50))
                || (amountOff != null && amountOff.signum() <= 0) || maxUses <= 0 || maxUses > 10_000) {
            throw new BusinessException(ErrorCode.COUPON_INVALID,
                    "Up to 50% off, a positive amount, and between 1 and 10,000 uses");
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "The end date has passed");
        }
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM seller_coupons WHERE seller_id = ?", Integer.class,
                sellerId);
        if (count != null && count >= LIMIT) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "You can keep at most " + LIMIT + " codes");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO seller_coupons (id, seller_id, code, percent_off, amount_off, min_price, followers_only,
                        max_uses, expires_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, id, sellerId, clean, percentOff, amountOff, minPrice, followersOnly, maxUses,
                    expiresAt == null ? null : expiresAt.atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC));
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "That code is taken");
        }
        return mine(sellerId).stream().filter(coupon -> coupon.id().equals(id)).findFirst().orElseThrow();
    }

    public List<Coupon> mine(UUID sellerId) {
        return jdbc.query("SELECT * FROM seller_coupons WHERE seller_id = ? ORDER BY created_at DESC",
                (rs, n) -> coupon(rs), sellerId);
    }

    public Coupon setActive(UUID sellerId, String code, boolean active) {
        if (jdbc.update("UPDATE seller_coupons SET active = ? WHERE seller_id = ? AND code = ?", active, sellerId,
                PromoCodes.normalise(code)) == 0) {
            throw new ResourceNotFoundException(ErrorCode.COUPON_NOT_FOUND, "No code " + code);
        }
        return mine(sellerId).stream().filter(coupon -> coupon.code().equals(PromoCodes.normalise(code)))
                .findFirst().orElseThrow();
    }

    /**
     * EN: What the code takes off this price for this buyer, or why not. Whole thousands, never the whole price.
     * VI: Mã trừ bao nhiêu khỏi giá này cho người mua này, hoặc vì sao không. Làm tròn nghìn, không bao giờ trừ hết giá.
     */
    Valid check(String code, UUID sellerId, UUID buyerId, BigDecimal price, Instant now) {
        String clean = PromoCodes.normalise(code);
        Coupon coupon = jdbc.query("SELECT * FROM seller_coupons WHERE code = ?", (rs, n) -> coupon(rs), clean)
                .stream().findFirst()
                .filter(found -> sellerId.equals(sellerOf(found.id())))
                .orElseThrow(() -> new BusinessException(ErrorCode.COUPON_INVALID,
                        "That code does not belong to this seller"));
        if (!coupon.active() || (coupon.expiresAt() != null && !coupon.expiresAt().isAfter(now))
                || coupon.used() >= coupon.maxUses()) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "This code has ended or run out");
        }
        if (coupon.minPrice() != null && price.compareTo(coupon.minPrice()) < 0) {
            throw new BusinessException(ErrorCode.COUPON_INVALID,
                    "This code needs a price of at least " + coupon.minPrice().toPlainString());
        }
        if (coupon.followersOnly() && !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM seller_follows WHERE user_id = ? AND seller_id = ?)", Boolean.class,
                buyerId, sellerId))) {
            throw new BusinessException(ErrorCode.COUPON_INVALID, "This code is for the seller's followers");
        }
        BigDecimal off = coupon.percentOff() != null
                ? price.multiply(BigDecimal.valueOf(coupon.percentOff())).divide(BigDecimal.valueOf(100), 0,
                        RoundingMode.DOWN).divide(ROUND, 0, RoundingMode.DOWN).multiply(ROUND)
                : coupon.amountOff();
        BigDecimal ceiling = price.subtract(ROUND).max(BigDecimal.ZERO);
        return new Valid(coupon.code(), off.min(ceiling));
    }

    /** EN: Counts one use once the payment went through. / VI: Tính một lượt dùng khi khoản thanh toán đã qua. */
    void redeem(String code) {
        jdbc.update("UPDATE seller_coupons SET used = used + 1 WHERE code = ?", PromoCodes.normalise(code));
    }

    private UUID sellerOf(UUID couponId) {
        return jdbc.queryForObject("SELECT seller_id FROM seller_coupons WHERE id = ?", UUID.class, couponId);
    }

    private static Coupon coupon(ResultSet rs) throws SQLException {
        Timestamp expires = rs.getTimestamp("expires_at");
        Integer percent = (Integer) rs.getObject("percent_off");
        return new Coupon(rs.getObject("id", UUID.class), rs.getString("code"), percent, rs.getBigDecimal("amount_off"),
                rs.getBigDecimal("min_price"), rs.getBoolean("followers_only"), rs.getInt("max_uses"),
                rs.getInt("used"), expires == null ? null : expires.toInstant(), rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant());
    }
}
