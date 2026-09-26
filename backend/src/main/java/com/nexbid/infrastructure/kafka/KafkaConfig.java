package com.nexbid.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.TopicBuilder;
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

    /**
     * EN: Keep retrying a failed notification. Skipping it would commit the Kafka offset and silently lose
     *     a notice; the partition waits until the database or handler recovers. Two failures never recover,
     *     so they alone are logged and skipped instead of blocking their partition for good: an event that
     *     cannot be read, and one the database refuses outright (say, a user this database does not have).
     * VI: Tiếp tục thử lại thông báo lỗi. Bỏ qua sẽ commit Kafka offset và âm thầm mất thông báo;
     *     partition chờ cho tới khi database hoặc handler phục hồi. Hai loại lỗi không bao giờ tự hết, nên chỉ
     *     chúng bị ghi log rồi bỏ qua thay vì chặn partition mãi: sự kiện không đọc nổi, và sự kiện bị database
     *     từ chối hẳn (ví dụ trỏ tới user mà database này không có).
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, ex) -> log.error("Skipping an event that can never be handled: {} at {}-{}@{}: {}",
                        EventHeaders.typeOf(record), record.topic(), record.partition(), record.offset(),
                        NestedExceptionUtils.getMostSpecificCause(ex).getMessage()),
                new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
        handler.addNotRetryableExceptions(JacksonException.class, DataIntegrityViolationException.class);
        return handler;
    }
}
