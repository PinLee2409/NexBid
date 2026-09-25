package com.nexbid.infrastructure.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * EN: The topics (spec §19) and what a consumer does when handling an event keeps failing.
 * VI: Các topic (spec §19) và việc consumer làm khi xử lý một sự kiện cứ thất bại mãi.
 */
@Configuration
public class KafkaConfig {

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
     *     a notice; the partition waits until the database or handler recovers.
     * VI: Tiếp tục thử lại thông báo lỗi. Bỏ qua sẽ commit Kafka offset và âm thầm mất thông báo;
     *     partition chờ cho tới khi database hoặc handler phục hồi.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
