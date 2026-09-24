package com.nexbid.notification.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.nexbid.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /**
     * EN: Adds a notice. For the once-per-lot types, a second attempt for the same person and lot is
     *     dropped by the partial unique index instead of failing — so a job may safely run twice.
     * VI: Thêm một thông báo. Với các loại chỉ-một-lần-mỗi-lô, lần thử thứ hai cho cùng người và cùng lô bị
     *     index duy nhất có điều kiện bỏ qua thay vì báo lỗi — nên một job chạy hai lần vẫn an toàn.
     */
    @Modifying
    @Query(value = """
            INSERT INTO notifications (id, user_id, type, title, message, auction_id, is_read, created_at)
            VALUES (gen_random_uuid(), :userId, :type, :title, :message, :auctionId, FALSE, :createdAt)
            ON CONFLICT (user_id, auction_id, type)
                WHERE type IN ('AUCTION_STARTING', 'AUCTION_ENDING', 'AUCTION_WON', 'AUCTION_LOST')
                DO NOTHING
            """, nativeQuery = true)
    int insert(
            @Param("userId") UUID userId,
            @Param("type") String type,
            @Param("title") String title,
            @Param("message") String message,
            @Param("auctionId") UUID auctionId,
            @Param("createdAt") Instant createdAt);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.id = :id AND n.userId = :userId")
    int markRead(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.userId = :userId AND n.read = false")
    int markAllRead(@Param("userId") UUID userId);
}
