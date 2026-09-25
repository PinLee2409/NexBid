package com.nexbid.infrastructure.kafka;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * EN: Forgets handled events once Kafka no longer keeps them, since only a kept event can come back.
 * VI: Quên các sự kiện đã xử lý khi Kafka không còn giữ chúng, vì chỉ sự kiện còn được giữ mới có thể quay lại.
 */
@Component
@ConditionalOnProperty(name = "nexbid.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class ConsumedEventsPruner {

    private static final Logger log = LoggerFactory.getLogger(ConsumedEventsPruner.class);

    private final ConsumedEvents consumed;
    private final Duration retention;

    ConsumedEventsPruner(ConsumedEvents consumed, @Value("${nexbid.kafka.consumed-retention}") Duration retention) {
        this.consumed = consumed;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${nexbid.kafka.prune-interval}")
    void prune() {
        int forgotten = consumed.forgetBefore(Instant.now().minus(retention));
        if (forgotten > 0) {
            log.debug("Forgot {} handled events older than {}", forgotten, retention);
        }
    }
}
