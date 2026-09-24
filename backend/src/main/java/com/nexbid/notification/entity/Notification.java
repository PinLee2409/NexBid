package com.nexbid.notification.entity;

import java.time.Instant;
import java.util.UUID;

import com.nexbid.notification.NotificationType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * EN: One notice for one person. Rows are inserted by native statements (see NotificationRepository);
 *     the only thing that ever changes afterwards is whether it has been read.
 * VI: Một thông báo cho một người. Các dòng được thêm bằng câu lệnh native (xem NotificationRepository);
 *     sau đó chỉ duy nhất trạng thái đã đọc là còn thay đổi.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private NotificationType type;

    @Column(nullable = false, updatable = false, length = 160)
    private String title;

    @Column(nullable = false, updatable = false, length = 500)
    private String message;

    @Column(name = "auction_id", updatable = false)
    private UUID auctionId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // EN: Required by JPA. / VI: JPA bắt buộc phải có.
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public UUID getAuctionId() {
        return auctionId;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
