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

    // EN: The pattern escapes %, _ and ! with "!", so what the user typed matches literally.
    // VI: Mẫu tìm đã thoát %, _ và ! bằng "!", để thứ người dùng gõ được khớp đúng nguyên văn.
    @Query("select p.id from Product p where lower(p.name) like lower(?1) escape '!'")
    List<UUID> findIdsByNameLike(String pattern);

    @Query("select p.id from Product p where p.condition in ?1")
    List<UUID> findIdsByConditionIn(java.util.Collection<com.nexbid.product.ProductCondition> conditions);
}
