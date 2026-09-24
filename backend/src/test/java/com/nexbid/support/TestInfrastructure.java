package com.nexbid.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * EN: A real Postgres for tests. The schema uses LOWER() indexes, UUID and TIMESTAMPTZ, so H2 would test a lie.
 * VI: Postgres thật cho test. Schema dùng index LOWER(), UUID và TIMESTAMPTZ, nên H2 sẽ test sai sự thật.
 *
 * <p>EN: It also means `./mvnw test` no longer needs the development database to be running.
 * <p>VI: Nhờ vậy `./mvnw test` không còn cần database dev phải đang bật.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    // EN: @ServiceConnection wires the URL, user and password automatically — no property juggling.
    // VI: @ServiceConnection tự nối URL, user và password — không phải set thủ công.
    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}
