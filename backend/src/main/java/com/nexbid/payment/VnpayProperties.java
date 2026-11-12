package com.nexbid.payment;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EN: The merchant's VNPay settings (application.yml → nexbid.payment.vnpay).
 * VI: Cấu hình VNPay của merchant (application.yml → nexbid.payment.vnpay).
 */
@ConfigurationProperties("nexbid.payment.vnpay")
record VnpayProperties(String tmnCode, String hashSecret, String payUrl, String returnUrl, Duration timeout) {

    /** EN: Still the repository's placeholders. / VI: Vẫn là giá trị giữ chỗ trong repository. */
    boolean placeholder() {
        return tmnCode == null || tmnCode.contains("change-me") || hashSecret == null || hashSecret.contains("change-me");
    }
}
