package com.nexbid.notification;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.nexbid.user.SellerApplicationEvent;

/**
 * EN: Notices about the account itself. Written in the admin's transaction rather than through Kafka: one
 *     notice per decision, with nothing else waiting on it, so there is no load to take off the request.
 * VI: Thông báo về chính tài khoản. Ghi trong transaction của admin thay vì qua Kafka: mỗi quyết định một thông
 *     báo, không có gì khác chờ nó, nên chẳng có tải nào cần gỡ khỏi request.
 */
@Component
class AccountNotices {

    private final NotificationService notifications;

    AccountNotices(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** EN: The applicant hears the decision; sending a request tells nobody. / VI: Người nộp được báo quyết định; gửi yêu cầu thì không báo ai. */
    @EventListener
    void onSellerApplication(SellerApplicationEvent event) {
        switch (event.status()) {
            case APPROVED -> notifications.notify(event.userId(), NotificationType.SELLER_APPROVED,
                    "You can sell now", "Your request was approved. List your first item from the seller workspace.",
                    null, event.at());
            case REJECTED -> notifications.notify(event.userId(), NotificationType.SELLER_REJECTED,
                    // EN: The reason alone: both are capped at 500 characters. / VI: Chỉ lý do: cả hai đều giới hạn 500 ký tự.
                    "Seller request declined", event.reason(), null, event.at());
            case PENDING -> {
                // EN: The admin queue shows it. / VI: Hàng chờ của admin đã hiện nó.
            }
        }
    }
}
