package com.nexbid.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;

/**
 * EN: The real services the app talks to, in containers: Postgres, Redis (function 32) and Kafka (function 34).
 *     A real Postgres because the schema uses LOWER() indexes, UUID and TIMESTAMPTZ — H2 would test a lie.
 * VI: Các dịch vụ thật mà app nói chuyện cùng, chạy trong container: Postgres, Redis (chức năng 32) và
 *     Kafka (chức năng 34). Postgres thật vì schema dùng index LOWER(), UUID và TIMESTAMPTZ — H2 sẽ test sai sự thật.
 *
 * <p>EN: It also means `./mvnw test` needs none of the development services running.
 * <p>VI: Nhờ vậy `./mvnw test` không cần dịch vụ dev nào phải đang bật.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

    // EN: @ServiceConnection wires the URL, user and password automatically — no property juggling.
    // VI: @ServiceConnection tự nối URL, user và password — không phải set thủ công.
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }

    // EN: The native build starts in about a second, which matters with one broker per test context.
    // VI: Bản native khởi động trong khoảng một giây, quan trọng khi mỗi test context có một broker riêng.
    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka-native:4.2.1");
    }
}
