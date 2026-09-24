package com.nexbid.notification;

import com.nexbid.auction.PageView;

/**
 * EN: A page of notices plus the number the bell badge shows. The count covers every unread notice,
 *     not just the ones on this page.
 * VI: Một trang thông báo kèm con số hiện trên huy hiệu cái chuông. Con số tính mọi thông báo chưa đọc,
 *     không chỉ những cái nằm trên trang này.
 */
public record NotificationInbox(PageView<NotificationView> notifications, long unreadCount) {
}
