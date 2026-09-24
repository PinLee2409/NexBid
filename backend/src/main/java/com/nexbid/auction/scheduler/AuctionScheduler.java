package com.nexbid.auction.scheduler;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nexbid.auction.AuctionService;

/**
 * EN: Moves lots along when the clock says so (guide §25, §26). The rules live in AuctionService; this class
 *     only decides when to ask.
 * VI: Đẩy các lô sang trạng thái kế tiếp khi đồng hồ tới giờ (guide §25, §26). Luật nằm trong AuctionService;
 *     lớp này chỉ quyết định khi nào thì hỏi.
 */
@Component
@ConditionalOnProperty(name = "nexbid.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class AuctionScheduler {

    private static final Logger log = LoggerFactory.getLogger(AuctionScheduler.class);

    private final AuctionService auctions;
    private final int batchSize;

    AuctionScheduler(AuctionService auctions, @Value("${nexbid.scheduler.batch-size}") int batchSize) {
        this.auctions = auctions;
        this.batchSize = batchSize;
    }

    /**
     * EN: Fixed delay, not fixed rate — the next run waits for this one to finish, so two runs never
     *     overlap and fight over the same rows.
     * VI: Fixed delay chứ không phải fixed rate — lượt sau chờ lượt này xong, nên hai lượt không bao giờ
     *     chồng nhau và giành cùng những dòng dữ liệu.
     */
    @Scheduled(fixedDelayString = "${nexbid.scheduler.interval}")
    void tick() {
        Instant now = Instant.now();

        // EN: Close before open, both against the same instant. The two queries cannot pick the same lot,
        //     but one "now" per tick keeps a single run's decisions consistent with each other.
        // VI: Đóng trước rồi mở, cả hai theo cùng một thời điểm. Hai truy vấn không thể chọn trùng lô,
        //     nhưng một "bây giờ" cho mỗi lượt giúp các quyết định trong cùng lượt nhất quán với nhau.
        int ended = auctions.endDueAuctions(now, batchSize);
        int started = auctions.startDueAuctions(now, batchSize);

        if (ended > 0) {
            log.info("Closed {} auction(s)", ended);
        }
        if (started > 0) {
            log.info("Opened {} auction(s) for bidding", started);
        }
    }
}
