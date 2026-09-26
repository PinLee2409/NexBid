package com.nexbid.infrastructure.kafka;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * EN: Moves outbox rows to Kafka on its own thread, oldest first, deleting each once Kafka has it. Kafka
 *     being down only leaves rows waiting; bids never notice.
 * VI: Chuyển các dòng outbox sang Kafka trên luồng riêng, cũ nhất trước, xoá từng dòng khi Kafka đã nhận.
 *     Kafka sập thì các dòng chỉ nằm chờ; việc trả giá không hề hay biết.
 */
@Component
class OutboxRelay implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    /**
     * EN: One relay at a time across all instances, or two could send one lot's events out of order.
     * VI: Mỗi lúc chỉ một relay trên mọi instance, nếu không hai relay có thể gửi sự kiện của một lô sai thứ tự.
     */
    static final long RELAY_LOCK = 0x4E65784269644F42L;

    private static final RowMapper<Row> ROW = (rs, n) -> new Row(
            rs.getObject("id", UUID.class), rs.getString("topic"), rs.getString("message_key"),
            rs.getString("event_type"), rs.getString("payload"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final KafkaTemplate<String, String> kafka;
    private final int batchSize;
    private final Duration interval;
    private final Duration retryAfter;
    private final Duration sendTimeout;

    private final Semaphore signal = new Semaphore(0);
    private volatile boolean running;
    private volatile Thread worker;

    OutboxRelay(
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            KafkaTemplate<String, String> kafka,
            @Value("${nexbid.kafka.relay.batch-size}") int batchSize,
            @Value("${nexbid.kafka.relay.interval}") Duration interval,
            @Value("${nexbid.kafka.relay.retry-after}") Duration retryAfter,
            @Value("${nexbid.kafka.relay.send-timeout}") Duration sendTimeout) {

        this.jdbc = jdbc;
        this.tx = tx;
        this.kafka = kafka;
        this.batchSize = batchSize;
        this.interval = interval;
        this.retryAfter = retryAfter;
        this.sendTimeout = sendTimeout;
    }

    record Row(UUID id, String topic, String key, String type, String payload) {
    }

    private record Batch(int delivered, RuntimeException failure) {
    }

    /** EN: Something was just committed to the outbox. / VI: Vừa có thứ được commit vào outbox. */
    void wake() {
        signal.release();
    }

    @Override
    public void start() {
        running = true;
        worker = Thread.ofPlatform().name("outbox-relay").daemon().start(this::loop);
    }

    @Override
    public void stop() {
        running = false;
        Thread current = worker;
        if (current != null) {
            current.interrupt();
            try {
                current.join(Duration.ofSeconds(5));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try {
                // EN: A commit wakes it at once; the timeout is only a safety net. / VI: Commit đánh thức nó ngay; timeout chỉ là lưới an toàn.
                signal.tryAcquire(interval.toMillis(), TimeUnit.MILLISECONDS);
                signal.drainPermits();
                while (running && relayBatch() == batchSize) {
                    // EN: A full batch means more may be waiting. / VI: Đủ một lô nghĩa là có thể còn nữa.
                }
            } catch (InterruptedException ex) {
                return;
            } catch (RuntimeException ex) {
                log.warn("Kafka unavailable, events wait in the outbox for {}: {}", retryAfter, ex.getMessage());
                try {
                    Thread.sleep(retryAfter);
                    // EN: Try again straight after the pause, not a poll later. / VI: Thử lại ngay sau khi nghỉ, không đợi thêm một lượt quét.
                    signal.release();
                } catch (InterruptedException interrupted) {
                    return;
                }
            }
        }
    }

    /**
     * EN: Sends one batch and deletes what Kafka confirmed, up to the first failure, so order is kept on
     *     the retry. Returns how many went out.
     * VI: Gửi một lô và xoá những gì Kafka đã xác nhận, tới lỗi đầu tiên, để lần gửi lại vẫn giữ đúng thứ
     *     tự. Trả về số sự kiện đã đi.
     */
    int relayBatch() {
        Batch batch = tx.execute(status -> {
            Boolean mine = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(?)", Boolean.class, RELAY_LOCK);
            if (!Boolean.TRUE.equals(mine)) {
                return new Batch(0, null);
            }

            List<Row> rows = jdbc.query(
                    "SELECT id, topic, message_key, event_type, payload FROM outbox_events ORDER BY position LIMIT ?",
                    ROW, batchSize);

            List<UUID> delivered = new ArrayList<>();
            RuntimeException failure = null;
            for (Row row : rows) {
                try {
                    // EN: Do not send a later event until Kafka confirms this one. If this send fails,
                    //     a later event must not overtake it on the same partition.
                    // VI: Không gửi sự kiện sau trước khi Kafka xác nhận sự kiện này. Nếu gửi lỗi,
                    //     sự kiện sau không được vượt nó trên cùng partition.
                    send(row).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
                    delivered.add(row.id());
                } catch (ExecutionException ex) {
                    failure = new IllegalStateException(NestedExceptionUtils.getMostSpecificCause(ex).getMessage(), ex.getCause());
                    break;
                } catch (TimeoutException ex) {
                    failure = new IllegalStateException("No answer from Kafka within " + sendTimeout, ex);
                    break;
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    failure = new IllegalStateException("Interrupted while sending", ex);
                    break;
                }
            }

            if (!delivered.isEmpty()) {
                jdbc.update(con -> {
                    var delete = con.prepareStatement("DELETE FROM outbox_events WHERE id = ANY(?)");
                    delete.setArray(1, con.createArrayOf("uuid", delivered.toArray()));
                    return delete;
                });
            }
            return new Batch(delivered.size(), failure);
        });

        if (batch.failure() != null) {
            throw batch.failure();
        }
        return batch.delivered();
    }

    private CompletableFuture<?> send(Row row) {
        ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.key(), row.payload());
        record.headers().add(EventHeaders.ID, row.id().toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventHeaders.TYPE, row.type().getBytes(StandardCharsets.UTF_8));
        try {
            return kafka.send(record);
        } catch (RuntimeException ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }
}
