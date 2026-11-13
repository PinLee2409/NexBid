package com.nexbid.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;
import com.sun.net.httpserver.HttpServer;

/**
 * EN: The outbox: written with the event or not at all, outbid emails throttled except in a lot's final minutes, and
 *     delivered to Brevo with retries.
 * VI: Outbox email: được ghi cùng sự kiện hoặc không ghi gì, email bị vượt giá có giới hạn trừ những phút cuối của lô,
 *     và được giao tới Brevo kèm thử lại.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@Import(TestInfrastructure.class)
class MailerIntegrationTest {

    @Autowired
    private Mailer mailer;

    @Autowired
    private MailOutbox outbox;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    private UUID person(String name) {
        return users.create(name, name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@nexbid.com",
                "{noop}irrelevant", RoleName.BUYER).id();
    }

    private int rows(UUID userId, String kind) {
        return jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE user_id = ? AND kind = ?",
                Integer.class, userId, kind);
    }

    private int sendDue(MailSender sender, Instant at) {
        Integer sent = tx.execute(status -> sender.sendDue(at));
        return sent == null ? 0 : sent;
    }

    @Test
    void outbidEmailsAreThrottledUntilTheFinalMinutes() {
        UUID bidder = person("Throttle Bidder");
        UUID lot = UUID.randomUUID();
        MailFacts facts = MailFacts.lot(lot, "Seiko Diver", new BigDecimal("8000000"));
        Instant now = Instant.now();

        // EN: Two hours left: three outbids, one email. / VI: Còn hai giờ: ba lần bị vượt giá, một email.
        for (int i = 0; i < 3; i++) {
            tx.executeWithoutResult(status -> mailer.sendOutbid(bidder, facts, now.plus(Duration.ofHours(2)), now));
        }
        assertThat(rows(bidder, "OUTBID")).isEqualTo(1);

        // EN: Ten minutes left: every outbid is emailed. / VI: Còn mười phút: lần bị vượt giá nào cũng có email.
        Instant late = now.plus(Duration.ofMinutes(1));
        for (int i = 0; i < 2; i++) {
            tx.executeWithoutResult(status -> mailer.sendOutbid(bidder, facts, late.plus(Duration.ofMinutes(10)), late));
        }
        assertThat(rows(bidder, "OUTBID")).isEqualTo(3);

        // EN: Another lot is throttled on its own. / VI: Lô khác được giới hạn riêng.
        MailFacts other = MailFacts.lot(UUID.randomUUID(), "Casio", new BigDecimal("2000000"));
        tx.executeWithoutResult(status -> mailer.sendOutbid(bidder, other, now.plus(Duration.ofHours(2)), now));
        assertThat(rows(bidder, "OUTBID")).isEqualTo(4);
    }

    @Test
    void anEmailExistsOnlyIfItsEventCommits() {
        UUID person = person("Rollback Person");

        tx.executeWithoutResult(status -> {
            mailer.send(person, MailKind.PAYMENT_RECEIPT, MailFacts.lot(UUID.randomUUID(), "Lot", BigDecimal.TEN));
            status.setRollbackOnly();
        });
        assertThat(rows(person, "PAYMENT_RECEIPT")).isZero();

        tx.executeWithoutResult(status ->
                mailer.send(person, MailKind.PAYMENT_RECEIPT, MailFacts.lot(UUID.randomUUID(), "Lot", BigDecimal.TEN)));
        assertThat(rows(person, "PAYMENT_RECEIPT")).isEqualTo(1);
        // EN: No API key in tests, so nothing waits to be sent. / VI: Test không có API key, nên không có gì chờ gửi.
        assertThat(jdbc.queryForObject("SELECT status FROM email_outbox WHERE user_id = ?", String.class, person))
                .isEqualTo("SKIPPED");
    }

    @Test
    void theSenderPostsToBrevoAndRetriesWhenItFails() throws IOException {
        List<String> received = new CopyOnWriteArrayList<>();
        List<String> keys = new CopyOnWriteArrayList<>();
        AtomicInteger answer = new AtomicInteger(201);
        HttpServer brevo = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        brevo.createContext("/v3/smtp/email", exchange -> {
            keys.add(exchange.getRequestHeaders().getFirst("api-key"));
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"messageId\":\"<test@brevo>\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(answer.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        brevo.start();
        try {
            MailProperties properties = new MailProperties("test-key",
                    "http://127.0.0.1:" + brevo.getAddress().getPort() + "/v3/smtp/email",
                    "no-reply@nexbid.test", "NexBid", "http://localhost:3000", "", 50, 3,
                    Duration.ofMinutes(30), Duration.ofMinutes(15));
            MailSender sender = new MailSender(outbox, properties, tx);
            jdbc.update("UPDATE email_outbox SET status = 'SKIPPED' WHERE status = 'PENDING'");

            UUID person = person("Brevo Person");
            MailTemplates.Rendered mail = new MailTemplates("http://localhost:3000")
                    .render(MailKind.WELCOME, "Brevo Person", MailFacts.none());
            Instant now = Instant.now();
            tx.executeWithoutResult(status -> outbox.insert(person, "brevo@nexbid.com", "Brevo Person", MailKind.WELCOME,
                    null, mail, "PENDING", now, "http://localhost:3000/api/email/unsubscribe?token=abc"));

            // EN: Brevo is down: the row waits a minute and counts the attempt. / VI: Brevo sập: dòng chờ một phút và đếm lần thử.
            answer.set(503);
            assertThat(sendDue(sender, now)).isZero();
            assertThat(jdbc.queryForObject("SELECT status || ':' || attempts FROM email_outbox WHERE user_id = ?",
                    String.class, person)).isEqualTo("PENDING:1");
            assertThat(sendDue(sender, now)).isZero();

            answer.set(201);
            assertThat(sendDue(sender, now.plus(Duration.ofMinutes(2)))).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM email_outbox WHERE user_id = ?", String.class, person))
                    .isEqualTo("SENT");

            assertThat(keys).containsOnly("test-key");
            assertThat(received.getLast()).contains("\"email\":\"brevo@nexbid.com\"")
                    .contains("Chào mừng bạn đến với NexBid · Welcome to NexBid")
                    .contains("\"htmlContent\"").contains("\"textContent\"")
                    .contains("\"List-Unsubscribe\":\"<http://localhost:3000/api/email/unsubscribe?token=abc>\"")
                    .contains("List-Unsubscribe=One-Click");
        } finally {
            brevo.stop(0);
        }
    }
}
