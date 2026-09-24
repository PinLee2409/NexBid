package com.nexbid.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nexbid.product.CategoryStatus;
import com.nexbid.product.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByStatusOrderByNameAsc(CategoryStatus status);

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByNameIgnoreCase(String name);
}
