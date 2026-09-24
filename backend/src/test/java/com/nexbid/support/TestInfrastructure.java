package com.nexbid.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * EN: The real services the app talks to, in containers: Postgres and, since function 32, Redis (spec §32).
 *     A real Postgres because the schema uses LOWER() indexes, UUID and TIMESTAMPTZ — H2 would test a lie.
 * VI: Các dịch vụ thật mà app nói chuyện cùng, chạy trong container: Postgres và, từ chức năng 32, Redis
 *     (spec §32). Postgres thật vì schema dùng index LOWER(), UUID và TIMESTAMPTZ — H2 sẽ test sai sự thật.
 *
 * <p>EN: It also means `./mvnw test` needs neither the development database nor the dev Redis running.
 * <p>VI: Nhờ vậy `./mvnw test` không cần database hay Redis dev phải đang bật.
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
}
