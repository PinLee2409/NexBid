package com.nexbid.payment;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * EN: Expires unpaid payments on its own clock. Paying already checks the deadline itself, so this only
 *     tidies up — it never decides whether a late payment is accepted.
 * VI: Cho các khoản chưa trả hết hạn theo nhịp riêng. Bản thân việc thanh toán đã tự kiểm hạn chót, nên
 *     đây chỉ là dọn dẹp — nó không bao giờ quyết định một lần trả muộn có được nhận hay không.
 */
@Component
@ConditionalOnProperty(name = "nexbid.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class PaymentScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaymentScheduler.class);

    private final PaymentService payments;
    private final int batchSize;

    PaymentScheduler(PaymentService payments, @Value("${nexbid.scheduler.batch-size}") int batchSize) {
        this.payments = payments;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${nexbid.payment.expiry-interval}")
    void tick() {
        int expired = payments.expireOverdue(Instant.now(), batchSize);

        if (expired > 0) {
            log.info("Expired {} unpaid payment(s)", expired);
        }
    }
}
