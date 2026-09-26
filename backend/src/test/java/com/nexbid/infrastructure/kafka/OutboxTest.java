package com.nexbid.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import javax.sql.DataSource;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.modulith.events.Externalized;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexbid.support.TestInfrastructure;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The outbox (guide §36, spec §19): an event reaches Kafka if and only if its transaction commits, in
 *     the order it happened. The relay's poll is pushed out to 30 s here, so every delivery below also
 *     proves that a commit wakes the relay straight away.
 * VI: Outbox (guide §36, spec §19): sự kiện tới Kafka khi và chỉ khi transaction của nó commit, đúng thứ tự
 *     đã xảy ra. Lượt quét của relay ở đây bị đẩy ra 30 giây, nên mỗi lần giao bên dưới cũng chứng minh rằng
 *     commit đánh thức relay ngay lập tức.
 */
@SpringBootTest(properties = {
        "nexbid.scheduler.enabled=false",
        "nexbid.kafka.relay.interval=30s"
})
@Import(TestInfrastructure.class)
class OutboxTest {

    static final String TOPIC = "nexbid.test-probes";

    @Externalized(TOPIC + "::#{key()}")
    record Probe(String key, int seq) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeTopic {
        @Bean
        NewTopic probeTopic() {
            return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
        }
    }

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private OutboxRelay relay;

    @Autowired
    private ConsumerFactory<String, String> consumers;

    @Autowired
    private ObjectMapper json;

    private void publish(Probe probe) {
        tx.executeWithoutResult(status -> events.publishEvent(probe));
    }

    private int waiting(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE message_key = ?", Integer.class, key);
    }

    /**
     * EN: Reads the probe topic from the start until {@code count} records match, or the time runs out.
     * VI: Đọc topic probe từ đầu cho tới khi đủ {@code count} bản ghi khớp, hoặc hết thời gian.
     */
    private List<ConsumerRecord<String, String>> read(Predicate<ConsumerRecord<String, String>> match, int count,
            Duration within) {

        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (Consumer<String, String> consumer = consumers.createConsumer("probe-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.nanoTime() + within.toNanos();
            while (found.size() < count && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                    if (match.test(record)) {
                        found.add(record);
                    }
                });
            }
        }
        return found;
    }

    private static Predicate<ConsumerRecord<String, String>> keyed(String key) {
        return record -> key.equals(record.key());
    }

    @Test
    void aCommittedEventReachesKafkaWithItsIdTypeAndKeyAndLeavesTheOutbox() throws Exception {
        long start = System.nanoTime();
        publish(new Probe("commit", 1));

        List<ConsumerRecord<String, String>> got = read(keyed("commit"), 1, Duration.ofSeconds(10));

        assertThat(got).hasSize(1);
        ConsumerRecord<String, String> record = got.getFirst();
        assertThat(EventHeaders.typeOf(record)).isEqualTo("OutboxTest.Probe");
        assertThat(EventHeaders.idOf(record)).isNotNull();
        assertThat(json.readValue(record.value(), Probe.class)).isEqualTo(new Probe("commit", 1));
        // EN: Well inside the 30 s poll: the commit itself woke the relay.
        // VI: Nhanh hơn hẳn lượt quét 30 giây: chính commit đã đánh thức relay.
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
        assertThat(waiting("commit")).isZero();
    }

    @Test
    void anEventFromARolledBackTransactionNeverLeaves() {
        tx.executeWithoutResult(status -> {
            events.publishEvent(new Probe("rollback", 1));
            assertThat(waiting("rollback")).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(waiting("rollback")).isZero();

        // EN: A later event getting through proves the relay ran, and still nothing of the rolled-back one.
        // VI: Một sự kiện sau đó đi qua được chứng tỏ relay đã chạy, mà vẫn không có gì từ sự kiện bị rollback.
        publish(new Probe("after-rollback", 1));
        assertThat(read(keyed("after-rollback"), 1, Duration.ofSeconds(10))).hasSize(1);
        assertThat(read(keyed("rollback"), 1, Duration.ofSeconds(1))).isEmpty();
    }

    @Test
    void eachKeysEventsArriveInTheOrderTheyWereCommitted() throws Exception {
        // EN: Four writers at once, each with its own key and its own sequence.
        // VI: Bốn luồng ghi cùng lúc, mỗi luồng một khoá và một dãy số riêng.
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Future<?>> writers = new ArrayList<>();
            for (int w = 0; w < 4; w++) {
                String key = "order-" + w;
                writers.add(pool.submit(() -> {
                    for (int seq = 1; seq <= 30; seq++) {
                        publish(new Probe(key, seq));
                    }
                }));
            }
            for (Future<?> writer : writers) {
                writer.get();
            }
        }

        List<ConsumerRecord<String, String>> got =
                read(record -> record.key().startsWith("order-"), 120, Duration.ofSeconds(20));

        assertThat(got).hasSize(120);
        for (int w = 0; w < 4; w++) {
            String key = "order-" + w;
            List<Integer> seqs = got.stream().filter(keyed(key))
                    .map(record -> json.readValue(record.value(), Probe.class).seq()).toList();
            assertThat(seqs).as(key).isEqualTo(java.util.stream.IntStream.rangeClosed(1, 30).boxed().toList());
        }
    }

    @Test
    void publishingOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> events.publishEvent(new Probe("no-tx", 1)))
                .hasMessageContaining("must be published inside a transaction");
        assertThat(waiting("no-tx")).isZero();
    }

    @Test
    void onlyOneRelayWorksAtATime() throws Exception {
        // EN: Another instance's relay is mid-batch: it holds the relay lock.
        // VI: Relay của một instance khác đang chạy dở một lô: nó đang giữ khoá relay.
        try (Connection other = dataSource.getConnection()) {
            other.createStatement().execute("SELECT pg_advisory_lock(" + OutboxRelay.RELAY_LOCK + ")");

            publish(new Probe("locked", 1));
            assertThat(relay.relayBatch()).isZero();
            Thread.sleep(500);
            assertThat(waiting("locked")).isEqualTo(1);

            other.createStatement().execute("SELECT pg_advisory_unlock(" + OutboxRelay.RELAY_LOCK + ")");
        }

        relay.wake();
        assertThat(read(keyed("locked"), 1, Duration.ofSeconds(10))).hasSize(1);
        assertThat(waiting("locked")).isZero();
    }
}
