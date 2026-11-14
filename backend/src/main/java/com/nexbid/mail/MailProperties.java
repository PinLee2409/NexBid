package com.nexbid.mail;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EN: Email settings (application.yml → nexbid.mail).
 * VI: Cấu hình email (application.yml → nexbid.mail).
 */
@ConfigurationProperties("nexbid.mail")
record MailProperties(
        String brevoApiKey,
        String brevoUrl,
        String senderEmail,
        String senderName,
        String siteUrl,
        String assetUrl,
        int batchSize,
        int maxAttempts,
        Duration outbidThrottle,
        Duration outbidFinalWindow) {

    /** EN: Without a key, emails are logged instead of sent. / VI: Không có key thì email chỉ được ghi log chứ không gửi. */
    boolean sending() {
        return brevoApiKey != null && !brevoApiKey.isBlank();
    }
}
