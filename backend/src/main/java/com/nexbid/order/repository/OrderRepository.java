package com.nexbid.order.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.nexbid.order.OrderStatus;
import com.nexbid.order.entity.Order;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    List<Order> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);

    Page<Order> findByStatusIn(Collection<OrderStatus> statuses, Pageable pageable);

    Optional<Order> findByIdAndBuyerId(UUID id, UUID buyerId);

    Optional<Order> findByPaymentId(UUID paymentId);

    long countBySellerIdAndStatusIn(UUID sellerId, Collection<OrderStatus> statuses);

    /**
     * EN: Locked, so a seller shipping and an admin refunding the same order cannot both win.
     * VI: Khoá lại, để người bán gửi hàng và admin hoàn tiền cùng một đơn không thể cùng thành công.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findLockedById(UUID id);

    /**
     * EN: Shipped orders past the wait, locked and skipped when another instance holds them.
     * VI: Đơn đã gửi quá thời gian chờ, được khoá và bỏ qua nếu instance khác đang giữ.
     */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM orders WHERE status = 'PROCESSING' AND shipped_at < :before
            ORDER BY shipped_at LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Order> lockShippedBefore(@org.springframework.data.repository.query.Param("before") java.time.Instant before,
            @org.springframework.data.repository.query.Param("limit") int limit);

    /** EN: Delivered (per the carrier) before the time given, still unconfirmed, locked. / VI: Đã giao (theo đơn vị vận chuyển) trước thời điểm cho trước, chưa xác nhận, được khoá. */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM orders WHERE status = 'PROCESSING' AND delivered_at < :before
            ORDER BY delivered_at LIMIT :limit FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Order> lockDeliveredBefore(@org.springframework.data.repository.query.Param("before") java.time.Instant before,
            @org.springframework.data.repository.query.Param("limit") int limit);

    /** EN: Shipped with a carrier, not yet delivered, for tracking. / VI: Đã gửi qua một đơn vị vận chuyển, chưa giao, để theo dõi. */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM orders WHERE status = 'PROCESSING' AND upper(carrier) = :carrier AND tracking_number IS NOT NULL
              AND delivered_at IS NULL ORDER BY shipped_at LIMIT :limit
            """, nativeQuery = true)
    List<Order> inTransitWith(@org.springframework.data.repository.query.Param("carrier") String carrier,
            @org.springframework.data.repository.query.Param("limit") int limit);

    /** EN: The order behind a carrier's parcel code. / VI: Đơn đứng sau mã vận đơn của đơn vị vận chuyển. */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT * FROM orders WHERE upper(carrier) = :carrier AND upper(tracking_number) = upper(:code)
            ORDER BY created_at DESC LIMIT 1 FOR UPDATE
            """, nativeQuery = true)
    java.util.Optional<Order> lockByParcel(@org.springframework.data.repository.query.Param("carrier") String carrier,
            @org.springframework.data.repository.query.Param("code") String code);

    /** EN: Shipped, not yet confirmed, shipped inside the window. / VI: Đã gửi, chưa xác nhận, gửi trong khoảng đã cho. */
    List<Order> findByStatusAndShippedAtBetween(OrderStatus status, java.time.Instant from, java.time.Instant to);
}
