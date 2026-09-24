package com.nexbid.product.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.nexbid.product.entity.ProductImage;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderBySortOrderAsc(UUID productId);

    Optional<ProductImage> findByIdAndProductId(UUID id, UUID productId);

    int countByProductId(UUID productId);

    /**
     * EN: Only the file names. Loading the entities would leave them in the persistence context pointing at
     *     a product that is about to be deleted, and the flush would fail.
     * VI: Chỉ lấy tên file. Nạp cả entity sẽ để chúng nằm lại trong persistence context trỏ vào một sản phẩm
     *     sắp bị xoá, và lúc flush sẽ lỗi.
     */
    @Query("select i.imageUrl from ProductImage i where i.product.id = ?1")
    List<String> findUrlsByProductId(UUID productId);

    @Modifying
    void deleteByProductId(UUID productId);
}
