package com.nexbid.notification;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.PageView;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.notification.dto.NotificationQuery;
import com.nexbid.notification.entity.Notification;
import com.nexbid.notification.repository.NotificationRepository;

/**
 * EN: The bell (guide §29). Notices are created directly by this service in the first version; Kafka
 *     takes over the delivery later (guide §36) without changing what is stored.
 * VI: Cái chuông (guide §29). Ở phiên bản đầu, thông báo được service này tạo trực tiếp; sau này Kafka sẽ
 *     đảm nhận việc chuyển phát (guide §36) mà không đổi những gì được lưu.
 */
@Service
public class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /**
     * EN: Records a notice, dated when the event happened rather than when it was written, so notices
     *     written in parallel still read in the right order. Returns 0 for a once-per-lot repeat.
     * VI: Ghi một thông báo, đề ngày theo lúc sự việc xảy ra chứ không theo lúc ghi, để các thông báo ghi
     *     song song vẫn hiện đúng thứ tự. Trả về 0 nếu là lần lặp của loại chỉ-một-lần-mỗi-lô.
     */
    @Transactional
    public int notify(
            UUID userId, NotificationType type, String title, String message, UUID auctionId, Instant occurredAt) {
        return notifications.insert(userId, type.name(), title, message, auctionId, occurredAt);
    }

    public NotificationInbox inboxOf(UUID userId, NotificationQuery query) {
        var page = notifications.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(query.zeroBasedPage(), query.size()));

        return new NotificationInbox(
                PageView.of(page, NotificationService::toView),
                notifications.countByUserIdAndReadFalse(userId));
    }

    /**
     * EN: Marks one notice read. Someone else's notice answers 404, the same as one that does not exist.
     * VI: Đánh dấu một thông báo đã đọc. Thông báo của người khác trả 404, y như thông báo không tồn tại.
     */
    @Transactional
    public NotificationView markRead(UUID userId, UUID notificationId) {
        notifications.markRead(notificationId, userId);

        return notifications.findByIdAndUserId(notificationId, userId)
                .map(NotificationService::toView)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.NOTIFICATION_NOT_FOUND, "No notification with id " + notificationId));
    }

    @Transactional
    public MarkedRead markAllRead(UUID userId) {
        return new MarkedRead(notifications.markAllRead(userId));
    }

    /** EN: How many were changed; zero when there was nothing unread. / VI: Số thông báo được đổi; 0 nếu không có gì chưa đọc. */
    public record MarkedRead(int marked) {
    }

    private static NotificationView toView(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getAuctionId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
