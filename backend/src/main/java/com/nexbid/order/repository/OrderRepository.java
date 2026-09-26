package com.nexbid.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexbid.order.entity.Order;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    Optional<Order> findByIdAndBuyerId(UUID id, UUID buyerId);

    Optional<Order> findByPaymentId(UUID paymentId);
}
