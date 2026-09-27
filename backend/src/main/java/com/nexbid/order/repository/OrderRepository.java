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

    /**
     * EN: Locked, so a seller shipping and an admin refunding the same order cannot both win.
     * VI: Khoá lại, để người bán gửi hàng và admin hoàn tiền cùng một đơn không thể cùng thành công.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findLockedById(UUID id);
}
