package com.nexbid.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * EN: Delivers queued emails through Brevo's transactional API (POST /v3/smtp/email). A failure is retried with a
 *     growing wait — 1, 2, 4, 8… minutes — up to the configured attempts, then the row is left FAILED for a person to
 *     look at. Runs only where the scheduler switch is on.
 * VI: Giao các email đang chờ qua API giao dịch của Brevo (POST /v3/smtp/email). Lỗi thì thử lại với thời gian chờ tăng
 *     dần — 1, 2, 4, 8… phút — tới số lần đã cấu hình, sau đó để dòng ở trạng thái FAILED cho người xem. Chỉ chạy ở
 *     nơi bật công tắc scheduler.
 */
@Component
@ConditionalOnProperty(name = "nexbid.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class MailSender {

    private static final Logger log = LoggerFactory.getLogger(MailSender.class);

    private final MailOutbox outbox;
    private final MailProperties properties;
    private final TransactionTemplate tx;
    private final RestClient brevo;

    MailSender(MailOutbox outbox, MailProperties properties, TransactionTemplate tx) {
        this.outbox = outbox;
        this.properties = properties;
        this.tx = tx;
        this.brevo = RestClient.builder().baseUrl(properties.brevoUrl()).build();
    }

    @Scheduled(fixedDelayString = "${nexbid.mail.send-interval}")
    void tick() {
        if (!properties.sending()) {
            return;
        }
        Integer sent = tx.execute(status -> sendDue(Instant.now()));
        if (sent != null && sent > 0) {
            log.info("Sent {} email(s) through Brevo", sent);
        }
    }

    /** EN: One batch, in the caller's transaction so the claimed rows stay locked. / VI: Một lô, trong transaction của bên gọi để các dòng đã nhận vẫn bị khoá. */
    int sendDue(Instant now) {
        List<MailOutbox.Pending> due = outbox.claimDue(now, properties.batchSize());
        int sent = 0;
        for (MailOutbox.Pending mail : due) {
            try {
                deliver(mail);
                outbox.markSent(mail.id(), now);
                sent++;
            } catch (RestClientException ex) {
                int attempt = mail.attempts() + 1;
                boolean giveUp = attempt >= properties.maxAttempts();
                outbox.markFailed(mail.id(), ex.getMessage(), giveUp, Duration.ofMinutes(1L << Math.min(attempt - 1, 10)), now);
                log.warn("Brevo refused email {} (attempt {}{}): {}", mail.id(), attempt, giveUp ? ", giving up" : "",
                        ex.getMessage());
            }
        }
        return sent;
    }

    private void deliver(MailOutbox.Pending mail) {
        Map<String, Object> body = new java.util.HashMap<>(Map.of(
                "sender", Map.of("email", properties.senderEmail(), "name", properties.senderName()),
                "to", List.of(mail.toName() == null
                        ? Map.of("email", mail.toEmail())
                        : Map.of("email", mail.toEmail(), "name", mail.toName())),
                "subject", mail.subject(),
                "htmlContent", mail.html(),
                "textContent", mail.text()));
        if (mail.unsubscribeUrl() != null) {
            // EN: Mail apps show their own "Unsubscribe" button from these (RFC 2369, RFC 8058).
            // VI: Ứng dụng mail dựa vào đây để hiện nút "Huỷ đăng ký" của riêng nó (RFC 2369, RFC 8058).
            body.put("headers", Map.of(
                    "List-Unsubscribe", "<" + mail.unsubscribeUrl() + ">",
                    "List-Unsubscribe-Post", "List-Unsubscribe=One-Click"));
        }
        brevo.post()
                .header("api-key", properties.brevoApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
