package com.nexbid.alert;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * EN: Sends the daily new-lot email at a fixed hour, Vietnam time. Rows are locked and marked as they are sent, so two
 *     instances running at once cannot send the same lots twice.
 * VI: Gửi email lô mới hằng ngày vào một giờ cố định, giờ Việt Nam. Các dòng được khoá và đánh dấu khi gửi, nên hai
 *     instance chạy cùng lúc không gửi trùng lô.
 */
@Component
@ConditionalOnProperty(name = "nexbid.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class DigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(DigestScheduler.class);

    private final AlertService alerts;

    DigestScheduler(AlertService alerts) {
        this.alerts = alerts;
    }

    @Scheduled(cron = "${nexbid.alert.digest-cron}", zone = "Asia/Ho_Chi_Minh")
    void daily() {
        int sent = alerts.sendDigests(Instant.now());
        if (sent > 0) {
            log.info("Queued {} new-lot digest email(s)", sent);
        }
    }
}
