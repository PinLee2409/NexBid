package com.nexbid.order.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexbid.order.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByOrderId(UUID orderId);

    List<Review> findByOrderIdIn(Collection<UUID> orderIds);

    Page<Review> findBySellerId(UUID sellerId, Pageable pageable);

    /** EN: Average and count in one query; the average is null while there are none. / VI: Trung bình và số lượng trong một truy vấn; trung bình là null khi chưa có đánh giá. */
    @Query("SELECT AVG(r.rating) AS average, COUNT(r) AS count FROM Review r WHERE r.sellerId = :sellerId")
    Score scoreOf(@Param("sellerId") UUID sellerId);

    interface Score {
        Double getAverage();

        long getCount();
    }
}
