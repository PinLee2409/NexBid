package com.nexbid.notification;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * EN: Asks for heads-up notices on its own clock. Slower than the auction clock on purpose: "soon" is
 *     measured in minutes, and every run re-reads the lots in the window.
 * VI: Hỏi gửi thông báo nhắc trước theo nhịp riêng. Cố ý chậm hơn đồng hồ đấu giá: "sắp" tính bằng phút,
 *     và mỗi lượt chạy đều đọc lại các lô trong khoảng thời gian đó.
 */
@Component
@ConditionalOnProperty(name = "nexbid.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class HeadsUpScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeadsUpScheduler.class);

    private final NotificationTriggers triggers;

    HeadsUpScheduler(NotificationTriggers triggers) {
        this.triggers = triggers;
    }

    @Scheduled(fixedDelayString = "${nexbid.notification.heads-up-interval}")
    void tick() {
        int sent = triggers.sendHeadsUps(Instant.now());

        if (sent > 0) {
            log.info("Sent {} heads-up notification(s)", sent);
        }
    }
}
