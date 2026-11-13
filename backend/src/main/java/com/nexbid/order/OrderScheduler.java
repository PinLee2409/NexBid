package com.nexbid.order;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * EN: Completes shipped orders whose buyer stayed silent past {@code nexbid.order.auto-complete-after}.
 * VI: Hoàn tất các đơn đã gửi mà người mua im lặng quá {@code nexbid.order.auto-complete-after}.
 */
@Component
@ConditionalOnProperty(name = "nexbid.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class OrderScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderScheduler.class);

    private final OrderService orders;
    private final Duration wait;
    private final int batchSize;

    OrderScheduler(
            OrderService orders,
            @Value("${nexbid.order.auto-complete-after}") Duration wait,
            @Value("${nexbid.scheduler.batch-size}") int batchSize) {
        this.orders = orders;
        this.wait = wait;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${nexbid.order.auto-complete-interval}")
    void tick() {
        int completed = orders.completeOverdue(Instant.now().minus(wait), batchSize);
        if (completed > 0) {
            log.info("Completed {} shipped order(s) the buyer did not confirm in {}", completed, wait);
        }
    }
}
