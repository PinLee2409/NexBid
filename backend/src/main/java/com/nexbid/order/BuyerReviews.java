package com.nexbid.order;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.order.entity.Order;
import com.nexbid.order.repository.OrderRepository;
import com.nexbid.user.BuyerStanding;

/**
 * EN: Sellers rate their buyers (1–5, once per completed order), and see a buyer's record beside each order: purchases
 *     completed, wins never paid for, and the average rating sellers gave. Ratings are for sellers only; buyers do not
 *     see them.
 * VI: Người bán đánh giá người mua (1–5, một lần mỗi đơn hoàn tất), và thấy hồ sơ người mua cạnh mỗi đơn: số đơn đã hoàn
 *     tất, số lần thắng mà không trả tiền, và điểm trung bình người bán đã cho. Đánh giá chỉ dành cho người bán; người mua
 *     không thấy.
 */
@Service
public class BuyerReviews {

    private final JdbcTemplate jdbc;
    private final OrderRepository orders;
    private final BuyerStanding standing;

    BuyerReviews(JdbcTemplate jdbc, OrderRepository orders, BuyerStanding standing) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.standing = standing;
    }

    /**
     * EN: The order row is the lock, so a double click leaves one rating. Another seller's order answers 404.
     * VI: Dòng đơn hàng là khoá, nên bấm hai lần chỉ để lại một đánh giá. Đơn của người bán khác trả 404.
     */
    @Transactional
    public OrderView.BuyerReview review(UUID sellerId, UUID orderId, int rating, String comment) {
        Order order = orders.findLockedById(orderId)
                .filter(found -> found.getSellerId().equals(sellerId))
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ORDER_NOT_FOUND, "No order with id " + orderId));
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.BUYER_REVIEW_NOT_ALLOWED, "Only a completed order's buyer can be rated");
        }
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM buyer_reviews WHERE order_id = ?", Integer.class,
                orderId);
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.REVIEW_EXISTS, "This buyer has already been rated for this order");
        }
        String text = comment == null || comment.isBlank() ? null : comment.trim();
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO buyer_reviews (id, order_id, auction_id, seller_id, buyer_id, rating, comment, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), orderId, order.getAuctionId(), sellerId, order.getBuyerId(), rating, text,
                now.atOffset(ZoneOffset.UTC));
        return new OrderView.BuyerReview(rating, text, now);
    }

    /** EN: The seller's own rating on each order, where given. / VI: Đánh giá của người bán trên từng đơn, nếu có. */
    Map<UUID, OrderView.BuyerReview> forOrders(Collection<UUID> orderIds) {
        Map<UUID, OrderView.BuyerReview> found = new HashMap<>();
        if (orderIds.isEmpty()) {
            return found;
        }
        jdbc.query("SELECT order_id, rating, comment, created_at FROM buyer_reviews WHERE order_id = ANY (?)", rs -> {
            found.put(rs.getObject(1, UUID.class),
                    new OrderView.BuyerReview(rs.getInt(2), rs.getString(3), rs.getTimestamp(4).toInstant()));
        }, (Object) orderIds.toArray(UUID[]::new));
        return found;
    }

    /** EN: Each buyer's record across all sellers. / VI: Hồ sơ của từng người mua trên mọi người bán. */
    Map<UUID, OrderView.BuyerRecord> recordsOf(Collection<UUID> buyerIds) {
        Map<UUID, OrderView.BuyerRecord> records = new HashMap<>();
        if (buyerIds.isEmpty()) {
            return records;
        }
        UUID[] ids = buyerIds.stream().distinct().toArray(UUID[]::new);
        Map<UUID, Integer> completed = new HashMap<>();
        jdbc.query("SELECT buyer_id, COUNT(*) FROM orders WHERE status = 'COMPLETED' AND buyer_id = ANY (?) GROUP BY buyer_id",
                rs -> {
                    completed.put(rs.getObject(1, UUID.class), rs.getInt(2));
                }, (Object) ids);
        Map<UUID, BigDecimal> averages = new HashMap<>();
        Map<UUID, Integer> counts = new HashMap<>();
        jdbc.query("SELECT buyer_id, AVG(rating), COUNT(*) FROM buyer_reviews WHERE buyer_id = ANY (?) GROUP BY buyer_id",
                rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    averages.put(id, rs.getBigDecimal(2).setScale(1, RoundingMode.HALF_UP));
                    counts.put(id, rs.getInt(3));
                }, (Object) ids);
        Map<UUID, Integer> unpaid = standing.unpaidWinsOf(java.util.List.of(ids));
        for (UUID id : ids) {
            records.put(id, new OrderView.BuyerRecord(completed.getOrDefault(id, 0), unpaid.getOrDefault(id, 0),
                    averages.get(id), counts.getOrDefault(id, 0)));
        }
        return records;
    }
}
