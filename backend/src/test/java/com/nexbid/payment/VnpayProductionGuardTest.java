package com.nexbid.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

/**
 * EN: Production must not start on the repository's placeholder VNPay keys.
 * VI: Production không được khởi động với khoá VNPay giữ chỗ trong repository.
 */
class VnpayProductionGuardTest {

    private static VnpayProperties keys(String tmnCode, String secret) {
        return new VnpayProperties(tmnCode, secret, "https://pay", "https://return", Duration.ofMinutes(15));
    }

    @Test
    void refusesThePlaceholders() {
        assertThatThrownBy(() -> new VnpayProductionGuard(keys("change-me", "change-me-local-vnpay-secret")))
                .hasMessageContaining("NEXBID_VNPAY_TMN_CODE");
        assertThatThrownBy(() -> new VnpayProductionGuard(keys("REALTMN1", "change-me-local-vnpay-secret")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void acceptsRealKeys() {
        assertThatCode(() -> new VnpayProductionGuard(keys("REALTMN1", "7f3a9c2e5b8d1f4a6c9e2b5d8f1a4c7e")))
                .doesNotThrowAnyException();
    }
}
