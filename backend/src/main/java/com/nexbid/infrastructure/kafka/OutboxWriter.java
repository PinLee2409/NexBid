package com.nexbid.infrastructure.kafka;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.Expression;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.Externalized;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Writes every event marked @Externalized into the outbox, inside the transaction that raised it
 *     (spec §19: the database commits first, Kafka hears after). Nothing here talks to Kafka.
 * VI: Ghi mọi sự kiện có @Externalized vào outbox, ngay trong transaction đã phát ra nó (spec §19: database
 *     commit trước, Kafka nghe sau). Không có gì ở đây nói chuyện với Kafka.
 */
@Component
class OutboxWriter {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final OutboxRelay relay;
    private final Map<Class<?>, Optional<Route>> routes = new ConcurrentHashMap<>();

    OutboxWriter(JdbcTemplate jdbc, ObjectMapper json, OutboxRelay relay) {
        this.jdbc = jdbc;
        this.json = json;
        this.relay = relay;
    }

    /**
     * EN: Where an event goes, read once per event type from "topic::#{key expression}".
     * VI: Sự kiện đi đâu, đọc một lần cho mỗi loại từ "topic::#{biểu thức khoá}".
     */
    record Route(String topic, Expression key, String type) {

        static Optional<Route> of(Class<?> eventType) {
            Externalized externalized = AnnotatedElementUtils.findMergedAnnotation(eventType, Externalized.class);
            if (externalized == null) {
                return Optional.empty();
            }

            String[] parts = externalized.target().split("::", 2);
            Expression key = parts.length == 2
                    ? PARSER.parseExpression(parts[1], ParserContext.TEMPLATE_EXPRESSION)
                    : null;
            return Optional.of(new Route(parts[0], key, EventHeaders.typeName(eventType)));
        }
    }

    @EventListener
    void write(Object event) {
        Route route = routes.computeIfAbsent(event.getClass(), Route::of).orElse(null);
        if (route == null) {
            return;
        }

        // EN: Outside a transaction the row would be committed alone, before the change it reports.
        // VI: Ngoài transaction thì dòng này sẽ được commit riêng, trước cả thay đổi mà nó báo cáo.
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(route.type() + " must be published inside a transaction");
        }

        jdbc.update("""
                INSERT INTO outbox_events (id, topic, message_key, event_type, payload, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                route.topic(),
                route.key() == null ? null : route.key().getValue(event, String.class),
                route.type(),
                json.writeValueAsString(event),
                OffsetDateTime.now(ZoneOffset.UTC));

        // EN: Nudge the relay the moment this commits, instead of waiting for its next poll.
        // VI: Đánh thức relay ngay khi commit xong, thay vì chờ tới lượt quét kế tiếp.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                relay.wake();
            }
        });
    }
}
