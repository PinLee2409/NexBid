package com.nexbid.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.infrastructure.kafka.ConsumedEvents;
import com.nexbid.infrastructure.kafka.EventHeaders;
import com.nexbid.payment.PaymentEvents;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Kafka delivers at least once. The consumer must turn a repeated delivery of one event into nothing,
 *     while two different events that happen to say the same thing stay two notices.
 * VI: Kafka giao ít nhất một lần. Consumer phải biến lần giao lặp của cùng một sự kiện thành không gì cả,
 *     trong khi hai sự kiện khác nhau tình cờ nói cùng một điều vẫn là hai thông báo.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
@ExtendWith(OutputCaptureExtension.class)
class NotificationConsumerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ConsumedEvents consumed;

    private UUID register(String email, String name, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"%s","email":"%s","password":"supersecret"}
                """.formatted(name, email)));
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"supersecret"}
                        """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("data").get("accessToken").asString();
        String me = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(me).get("data").get("id").asString());
    }

    /** EN: Any lot will do; the notice only needs something to point at. / VI: Lô nào cũng được; thông báo chỉ cần có chỗ để trỏ tới. */
    private UUID someLot(String tag) throws Exception {
        UUID seller = register("consumer.seller." + tag + "@nexbid.com", "Seller " + tag, RoleName.SELLER);
        UUID category = jdbc.queryForObject("SELECT id FROM categories LIMIT 1", UUID.class);
        UUID product = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, seller_id, category_id, name, description, condition, status, created_at, updated_at)
                VALUES (?, ?, ?, 'Pentax 67', 'A long description of the item.', 'LIKE_NEW', 'IN_AUCTION', now(), now())
                """, product, seller, category);
        UUID auction = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, current_price, minimum_increment,
                                      start_time, end_time, status, bid_count, version, created_at, updated_at)
                VALUES (?, ?, ?, 10000000, 10000000, 500000, now() - interval '2 hours', now() - interval '1 hour',
                        'DRAFT', 0, 0, now(), now())
                """, auction, product, seller);
        return auction;
    }

    // EN: Keyed by lot like the real events, so one lot's deliveries share a partition and keep their order.
    // VI: Khoá theo lô giống sự kiện thật, để các lần giao của một lô chung partition và giữ đúng thứ tự.
    private void deliver(UUID lot, UUID eventId, String type, Object event) {
        deliverRaw(lot, eventId, type, objectMapper.writeValueAsString(event));
    }

    private void deliverRaw(UUID lot, UUID eventId, String type, String body) {
        ProducerRecord<String, String> record = new ProducerRecord<>("nexbid.payments", lot.toString(), body);
        record.headers().add(EventHeaders.ID, eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventHeaders.TYPE, type.getBytes(StandardCharsets.UTF_8));
        kafka.send(record).join();
    }

    private int paymentNotices(UUID user) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'PAYMENT_SUCCESS'", Integer.class, user);
    }

    @Test
    void theSameEventDeliveredTwiceIsNotifiedOnce() throws Exception {
        UUID buyer = register("consumer.twice@nexbid.com", "Twice Buyer", RoleName.BUYER);
        UUID lot = someLot("twice");
        var paid = new PaymentEvents.Succeeded(UUID.randomUUID(), lot, buyer, new BigDecimal("12000000"), Instant.now());
        UUID eventId = UUID.randomUUID();
        String type = EventHeaders.typeName(PaymentEvents.Succeeded.class);

        deliver(lot, eventId, type, paid);
        deliver(lot, eventId, type, paid);
        // EN: A different event, delivered after both repeats, marks the point where they must have been read.
        // VI: Một sự kiện khác, giao sau cả hai lần lặp, đánh dấu thời điểm chắc chắn chúng đã được đọc.
        deliver(lot, UUID.randomUUID(), type,
                new PaymentEvents.Succeeded(UUID.randomUUID(), lot, buyer, new BigDecimal("12000000"), Instant.now()));

        await().atMost(Duration.ofSeconds(15)).until(() -> paymentNotices(buyer) >= 2);
        Thread.sleep(500);
        assertThat(paymentNotices(buyer)).isEqualTo(2);
    }

    @Test
    void eventsThatAreNotForNotificationsAreLeftAlone() throws Exception {
        UUID buyer = register("consumer.other@nexbid.com", "Other Buyer", RoleName.BUYER);
        UUID lot = someLot("other");
        UUID opened = UUID.randomUUID();

        deliver(lot, opened, EventHeaders.typeName(PaymentEvents.Opened.class),
                new PaymentEvents.Opened(UUID.randomUUID(), lot, buyer, new BigDecimal("12000000")));
        UUID marker = UUID.randomUUID();
        deliver(lot, marker, EventHeaders.typeName(PaymentEvents.Succeeded.class),
                new PaymentEvents.Succeeded(UUID.randomUUID(), lot, buyer, new BigDecimal("12000000"), Instant.now()));

        await().atMost(Duration.ofSeconds(15)).until(() -> paymentNotices(buyer) == 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ?", Integer.class, buyer))
                .isEqualTo(1);
        // EN: Not even marked as handled — it was never this consumer's to handle.
        // VI: Thậm chí không được đánh dấu là đã xử lý — nó chưa bao giờ là việc của consumer này.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumed_events WHERE event_id = ?", Integer.class, opened))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumed_events WHERE event_id = ?", Integer.class, marker))
                .isEqualTo(1);
    }

    @Test
    void anEventThatCannotBeReadIsSkippedInsteadOfHoldingUpTheOnesBehindIt(CapturedOutput output) throws Exception {
        UUID buyer = register("consumer.unreadable@nexbid.com", "Unreadable Buyer", RoleName.BUYER);
        UUID lot = someLot("unreadable");
        UUID broken = UUID.randomUUID();
        String type = EventHeaders.typeName(PaymentEvents.Succeeded.class);

        // EN: Same key, so the good event sits right behind the broken one on the same partition.
        // VI: Cùng khoá, nên sự kiện hợp lệ nằm ngay sau sự kiện hỏng trên cùng partition.
        deliverRaw(lot, broken, type, "{\"paymentId\": not json");
        deliver(lot, UUID.randomUUID(), type,
                new PaymentEvents.Succeeded(UUID.randomUUID(), lot, buyer, new BigDecimal("12000000"), Instant.now()));

        await().atMost(Duration.ofSeconds(15)).until(() -> paymentNotices(buyer) == 1);
        assertThat(output).contains("Skipping an event that cannot be read: PaymentEvents.Succeeded at nexbid.payments-");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consumed_events WHERE event_id = ?", Integer.class, broken))
                .isZero();
    }

    @Test
    void handledEventsAreForgottenOnlyOnceTheyAreOld() {
        UUID old = UUID.randomUUID();
        UUID recent = UUID.randomUUID();
        jdbc.update("INSERT INTO consumed_events (consumer, event_id, consumed_at) VALUES ('test', ?, now() - interval '8 days')", old);
        jdbc.update("INSERT INTO consumed_events (consumer, event_id, consumed_at) VALUES ('test', ?, now())", recent);

        consumed.forgetBefore(Instant.now().minus(Duration.ofDays(7)));

        assertThat(jdbc.queryForList("SELECT event_id FROM consumed_events WHERE consumer = 'test'", UUID.class))
                .containsExactly(recent);
    }
}
