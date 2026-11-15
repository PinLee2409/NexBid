package com.nexbid.order.shipping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * EN: Asks GHN about parcels in transit now and then, in case a webhook call never arrived. Does nothing while GHN is
 *     not connected.
 * VI: Thỉnh thoảng hỏi GHN về các kiện đang giao, phòng khi một lời gọi webhook không tới. Không làm gì khi chưa kết nối GHN.
 */
@Component
@ConditionalOnProperty(name = "nexbid.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class GhnScheduler {

    private static final Logger log = LoggerFactory.getLogger(GhnScheduler.class);

    private final GhnShipping ghn;
    private final int batchSize;

    GhnScheduler(GhnShipping ghn, @Value("${nexbid.scheduler.batch-size}") int batchSize) {
        this.ghn = ghn;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${nexbid.shipping.ghn.poll-interval}", initialDelayString = "${nexbid.shipping.ghn.poll-interval}")
    void tick() {
        int changed = ghn.refresh(batchSize);
        if (changed > 0) {
            log.info("GHN reported {} parcel update(s)", changed);
        }
    }
}
