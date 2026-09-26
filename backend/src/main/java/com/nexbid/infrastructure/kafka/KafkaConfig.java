package com.nexbid.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import tools.jackson.core.JacksonException;

/**
 * EN: The topics (spec §19) and what a consumer does when handling an event keeps failing.
 * VI: Các topic (spec §19) và việc consumer làm khi xử lý một sự kiện cứ thất bại mãi.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /** EN: Where an event that can never be handled is parked: its topic plus this. / VI: Nơi gửi sự kiện không bao giờ xử lý được: tên topic của nó cộng đuôi này. */
    public static final String DEAD_LETTER_SUFFIX = ".DLT";

    // EN: Everything about one lot, keyed by its id, so a lot's story is read in order.
    // VI: Mọi thứ về một lô, khoá theo id của lô, để câu chuyện của một lô được đọc đúng thứ tự.
    @Bean
    NewTopic auctionEvents() {
        return TopicBuilder.name("nexbid.auctions").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentEvents() {
        return TopicBuilder.name("nexbid.payments").partitions(3).replicas(1).build();
    }

    // EN: Same partition count as the originals: a dead letter keeps the partition it came from.
    // VI: Cùng số partition với topic gốc: thư chết giữ nguyên partition nơi nó xuất phát.
    @Bean
    NewTopic auctionDeadLetters() {
        return TopicBuilder.name("nexbid.auctions" + DEAD_LETTER_SUFFIX).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentDeadLetters() {
        return TopicBuilder.name("nexbid.payments" + DEAD_LETTER_SUFFIX).partitions(3).replicas(1).build();
    }

    /**
     * EN: Keep retrying a failed notification. Skipping it would commit the Kafka offset and silently lose
     *     a notice; the partition waits until the database or handler recovers. Two failures never recover,
     *     so they alone are set aside instead of blocking their partition for good: an event that cannot be
     *     read, and one the database refuses outright (say, a user this database does not have). Set aside
     *     means logged and published to the topic's dead-letter twin, where it can be inspected and replayed.
     * VI: Tiếp tục thử lại thông báo lỗi. Bỏ qua sẽ commit Kafka offset và âm thầm mất thông báo; partition chờ
     *     cho tới khi database hoặc handler phục hồi. Hai loại lỗi không bao giờ tự hết, nên chỉ chúng bị tách
     *     ra thay vì chặn partition mãi: sự kiện không đọc nổi, và sự kiện bị database từ chối hẳn (ví dụ trỏ tới
     *     user mà database này không có). Tách ra nghĩa là ghi log và gửi sang topic thư chết đi kèm, nơi có thể
     *     xem lại và phát lại.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka) {
        // EN: If the dead letter cannot be sent either, the handler retries the record rather than dropping it.
        // VI: Nếu thư chết cũng không gửi được, handler thử lại bản ghi chứ không bỏ nó đi.
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(kafka,
                (record, ex) -> new TopicPartition(record.topic() + DEAD_LETTER_SUFFIX, record.partition()));

        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> {
                    log.error("Skipping an event that can never be handled: {} at {}-{}@{}: {}",
                            EventHeaders.typeOf(record), record.topic(), record.partition(), record.offset(),
                            NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
                    deadLetters.accept(record, ex);
                },
                new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(JacksonException.class, DataIntegrityViolationException.class);
        return handler;
    }
}
