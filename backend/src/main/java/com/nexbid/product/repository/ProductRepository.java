package com.nexbid.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.nexbid.product.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findBySellerIdOrderByCreatedAtDesc(UUID sellerId);

    Optional<Product> findByIdAndSellerId(UUID id, UUID sellerId);

    long countByCategoryId(UUID categoryId);

    @Query("select p.id from Product p where p.category.id in ?1")
    List<UUID> findIdsByCategoryIdIn(java.util.Collection<UUID> categoryIds);
}
