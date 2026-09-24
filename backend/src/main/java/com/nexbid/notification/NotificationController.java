package com.nexbid.notification;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.notification.dto.NotificationQuery;

/**
 * EN: The bell's endpoints (guide §29). Every one of them reads the owner from the token only.
 * VI: Các endpoint của cái chuông (guide §29). Tất cả chỉ lấy chủ sở hữu từ token.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public ApiResponse<NotificationInbox> inbox(@AuthenticationPrincipal CurrentUser me, NotificationQuery query) {
        return ApiResponse.of(notifications.inboxOf(me.id(), query));
    }

    // EN: Declared before "/{id}/read" only for reading order; the two paths cannot collide.
    // VI: Khai trước "/{id}/read" chỉ để dễ đọc; hai đường dẫn không thể trùng nhau.
    @PatchMapping("/read-all")
    public ApiResponse<NotificationService.MarkedRead> readAll(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(notifications.markAllRead(me.id()));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationView> read(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        return ApiResponse.of(notifications.markRead(me.id(), id));
    }
}
