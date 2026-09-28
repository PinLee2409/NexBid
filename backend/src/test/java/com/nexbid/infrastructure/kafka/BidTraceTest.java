package com.nexbid.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import tools.jackson.databind.ObjectMapper;

/**
 * EN: One bid, one trace (spec §34): the request, its SQL, the outbox relay, the Kafka send and the consumer that
 *     writes the outbid notice all share the trace id the caller sent — although the relay and the consumer run on
 *     other threads, after the request has already answered.
 * VI: Một lượt trả giá, một trace (spec §34): request, các câu SQL của nó, outbox relay, lần gửi Kafka và consumer
 *     ghi thông báo bị vượt giá đều chung trace id mà bên gọi gửi lên — dù relay và consumer chạy trên luồng khác,
 *     sau khi request đã trả lời xong.
 */
@SpringBootTest(properties = {
        "nexbid.scheduler.enabled=false",
        "management.tracing.export.enabled=true",
        "management.tracing.export.otlp.enabled=false",
        "management.opentelemetry.tracing.export.schedule-delay=100ms"
})
@AutoConfigureMockMvc
@AutoConfigureTracing
@Import({ TestInfrastructure.class, BidTraceTest.Spans.class })
class BidTraceTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Spans {

        @Bean
        InMemorySpanExporter spans() {
            return InMemorySpanExporter.create();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private InMemorySpanExporter spans;

    @Autowired
    private ObservationRegistry observations;

    private String tokenFor(String tag, RoleName role) throws Exception {
        String email = "trace." + tag + "." + UUID.randomUUID() + "@nexbid.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Trace %s","email":"%s","password":"supersecret"}
                """.formatted(tag, email)));
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"supersecret"}
                        """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("accessToken").asString();
    }

    private String openLot() throws Exception {
        String seller = tokenFor("seller", RoleName.SELLER);
        String admin = tokenFor("admin", RoleName.ADMIN);
        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = json.readTree(categories).get("data").get(0).get("id").asString();
        String product = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Traced camera","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(categoryId)))
                .andReturn().getResponse().getContentAsString();
        String auction = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(json.readTree(product).get("data").get("id").asString(),
                                Instant.now().plus(Duration.ofMinutes(30)), Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(auction).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + id + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + id + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid", id);
        return id;
    }

    private static String randomHex(int bytes) {
        byte[] random = new byte[bytes];
        ThreadLocalRandom.current().nextBytes(random);
        return HexFormat.of().formatHex(random);
    }

    private List<SpanData> trace(String traceId) {
        return spans.getFinishedSpanItems().stream().filter(span -> span.getTraceId().equals(traceId)).toList();
    }

    /** EN: The statement as recorded ("jdbc.query[0]"); Hibernate writes its SQL in lower case. / VI: Câu lệnh như được ghi ("jdbc.query[0]"); Hibernate viết SQL chữ thường. */
    private static boolean runs(SpanData span, String statement) {
        return span.getAttributes().asMap().entrySet().stream().anyMatch(attribute ->
                attribute.getKey().getKey().startsWith("jdbc.query[")
                        && String.valueOf(attribute.getValue()).toLowerCase().contains(statement.toLowerCase()));
    }

    private static boolean ranSql(List<SpanData> trace, String statement) {
        return trace.stream().anyMatch(span -> span.getKind() == SpanKind.CLIENT && runs(span, statement));
    }

    private static SpanData only(List<SpanData> trace, SpanKind kind, String name) {
        return trace.stream().filter(span -> span.getKind() == kind && span.getName().contains(name)).findFirst()
                .orElseThrow(() -> new AssertionError("No " + kind + " span named " + name + " in " + trace));
    }

    @Test
    void aBidIsOneTraceFromTheRequestToTheNoticeItCauses() throws Exception {
        String lot = openLot();
        String first = tokenFor("first", RoleName.BUYER);
        String second = tokenFor("second", RoleName.BUYER);
        mockMvc.perform(post("/api/auctions/" + lot + "/bids").header("Authorization", "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000000}"))
                .andExpect(status().isCreated());

        // EN: As a tracing proxy in front of the app would send it. / VI: Như một proxy có trace đứng trước app sẽ gửi.
        String traceId = randomHex(16);
        mockMvc.perform(post("/api/auctions/" + lot + "/bids").header("Authorization", "Bearer " + second)
                        .header("traceparent", "00-" + traceId + "-" + randomHex(8) + "-01")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10500000}"))
                .andExpect(status().isCreated());

        // EN: The notice is the last thing the bid causes, written by the consumer on its own thread.
        // VI: Thông báo là việc cuối cùng mà lượt trả giá gây ra, do consumer ghi trên luồng riêng của nó.
        await().atMost(Duration.ofSeconds(20)).until(() -> ranSql(trace(traceId), "INSERT INTO notifications"));

        List<SpanData> trace = trace(traceId);
        SpanData request = only(trace, SpanKind.SERVER, "/bids");
        assertThat(ranSql(trace, "INSERT INTO bids")).isTrue();
        assertThat(ranSql(trace, "INSERT INTO outbox_events")).isTrue();

        // EN: The relay continues the request's span; Kafka's send and receive follow on from the relay.
        // VI: Relay nối tiếp span của request; việc gửi và nhận của Kafka nối tiếp từ relay.
        SpanData relay = only(trace, SpanKind.CONSUMER, "outbox relay nexbid.auctions");
        assertThat(relay.getParentSpanId()).isEqualTo(request.getSpanId());
        SpanData send = only(trace, SpanKind.PRODUCER, "nexbid.auctions send");
        assertThat(trace).anyMatch(span -> span.getSpanId().equals(send.getParentSpanId())
                && span.getName().startsWith("outbox relay"));
        assertThat(trace).anyMatch(span -> span.getKind() == SpanKind.CONSUMER
                && span.getName().equals("nexbid.auctions process")
                && trace.stream().anyMatch(parent -> parent.getKind() == SpanKind.PRODUCER
                        && parent.getSpanId().equals(span.getParentSpanId())));
    }

    @Test
    void backgroundWorkStartsNoTraces() throws Exception {
        spans.reset();
        // EN: A scheduler tick runs inside its switched-off observation. / VI: Nhịp scheduler chạy trong observation đã bị tắt của nó.
        Observation.createNotStarted("tasks.scheduled.execution", observations)
                .observe(() -> jdbc.queryForObject("SELECT count(*) FROM second_chance_offers", Long.class));
        // EN: And a few polls of an empty outbox. / VI: Và vài lượt quét một outbox trống.
        Thread.sleep(3_000);

        assertThat(spans.getFinishedSpanItems())
                .noneMatch(span -> runs(span, "outbox_events") || runs(span, "second_chance_offers"));
    }
}
