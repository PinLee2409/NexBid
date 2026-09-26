package com.nexbid.infrastructure.kafka;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * EN: Lets a consumer act on each event once, although Kafka may deliver it more than once.
 * VI: Giúp consumer chỉ xử lý mỗi sự kiện một lần, dù Kafka có thể giao nó nhiều hơn một lần.
 */
@Component
public class ConsumedEvents {

    private final JdbcTemplate jdbc;

    ConsumedEvents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * EN: True the first time this consumer sees this event. Must share the consumer's transaction, so the
     *     record and the work it guards commit together or not at all.
     * VI: Trả true ở lần đầu consumer này thấy sự kiện này. Phải dùng chung transaction với consumer, để dấu
     *     ghi nhận và phần việc nó bảo vệ cùng commit hoặc cùng không.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String consumer, UUID eventId) {
        if (eventId == null) {
            return true;
        }
        return jdbc.update("""
                INSERT INTO consumed_events (consumer, event_id, consumed_at) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """, consumer, eventId, OffsetDateTime.now(ZoneOffset.UTC)) == 1;
    }

    @Transactional
    public int forgetBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM consumed_events WHERE consumed_at < ?", cutoff.atOffset(ZoneOffset.UTC));
    }
}
