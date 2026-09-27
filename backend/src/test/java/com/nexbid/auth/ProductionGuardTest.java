package com.nexbid.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.nexbid.auth.jwt.JwtProperties;
import com.nexbid.auth.refresh.RefreshTokenProperties;

/**
 * EN: What the `prod` profile refuses to start with.
 * VI: Những gì profile `prod` từ chối khởi động cùng.
 */
class ProductionGuardTest {

    private static final String STRONG = "c2f1b7e04a9d6e3f8b2c5a7d1e9f0b3c4d6a8e2f1b7c9d0e";

    private static JwtProperties jwt(String secret) {
        return new JwtProperties(secret, Duration.ofMinutes(15), "nexbid");
    }

    private static RefreshTokenProperties refresh(boolean secure) {
        return new RefreshTokenProperties(Duration.ofDays(7), secure, Duration.ofHours(1));
    }

    @Test
    void theSecretsShippedInTheRepositoryAreRefused() {
        assertThat(ProductionGuard.problemsWith("nexbid-local-development-secret-key-change-me", true)).hasSize(1);
        assertThat(ProductionGuard.problemsWith("nexbid-docker-demo-secret-key-change-me", true)).hasSize(1);
    }

    @Test
    void aSecretTooShortForHmacSha256IsRefused() {
        assertThat(ProductionGuard.problemsWith("short-but-not-a-default", true))
                .singleElement().asString().contains("32 bytes");
    }

    @Test
    void aRefreshCookieAllowedOverPlainHttpIsRefused() {
        assertThat(ProductionGuard.problemsWith(STRONG, false)).singleElement().asString().contains("HTTPS");
    }

    @Test
    void startupStopsWithEveryProblemNamed() {
        assertThatThrownBy(() -> new ProductionGuard(jwt("nexbid-docker-demo-secret-key-change-me"), refresh(false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NEXBID_JWT_SECRET")
                .hasMessageContaining("NEXBID_REFRESH_COOKIE_SECURE");
    }

    @Test
    void aProperConfigurationStarts() {
        assertThat(ProductionGuard.problemsWith(STRONG, true)).isEmpty();
        new ProductionGuard(jwt(STRONG), refresh(true));
    }
}
