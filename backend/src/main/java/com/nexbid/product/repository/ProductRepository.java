package com.nexbid.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexbid.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);

    Optional<Product> findByIdAndSellerId(UUID id, UUID sellerId);

    long countByCategoryId(UUID categoryId);
}
