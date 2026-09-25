package com.nexbid.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * EN: A lot's events on Kafka (guide §36, spec §19): what arrives, in what order — and that Kafka is not on
 *     the bid's critical path: with the broker frozen, bids still go through at full speed.
 * VI: Sự kiện của một lô trên Kafka (guide §36, spec §19): cái gì tới, theo thứ tự nào — và Kafka không nằm
 *     trên đường găng của lượt trả giá: broker bị đóng băng thì bid vẫn đi qua với tốc độ bình thường.
 */
@SpringBootTest(properties = {
        "nexbid.scheduler.enabled=false",
        "nexbid.kafka.relay.retry-after=1s"
})
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
@ExtendWith(OutputCaptureExtension.class)
class AuctionEventsOnKafkaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaContainer kafka;

    @Autowired
    private ConsumerFactory<String, String> consumers;

    private String tokenFor(String email, String name, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"%s","email":"%s","password":"supersecret"}
                """.formatted(name, email)));
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"supersecret"}
                        """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    private UUID idOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("data").get("id").asString());
    }

    private String openLot(String tag) throws Exception {
        String seller = tokenFor(tag + ".seller@nexbid.com", "Seller " + tag, RoleName.SELLER);
        String admin = tokenFor(tag + ".admin@nexbid.com", "Admin " + tag, RoleName.ADMIN);
        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Rolleiflex 2.8F","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(categoryId)))
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();
        String auctionBody = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();
        String auction = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auction + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auction);
        return auction;
    }

    private void bid(String token, String auction, long amount) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auction + "/bids").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    private int outbidNotices(UUID user) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'OUTBID'",
                Integer.class, user);
    }

    private int waitingInOutbox() {
        return jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class);
    }

    /** EN: Reads a topic from the start until {@code count} records carry this key. / VI: Đọc topic từ đầu tới khi có đủ {@code count} bản ghi mang khoá này. */
    private List<ConsumerRecord<String, String>> read(String topic, String key, int count) {
        List<ConsumerRecord<String, String>> found = new ArrayList<>();
        try (Consumer<String, String> consumer = consumers.createConsumer("reader-" + UUID.randomUUID(), null)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (found.size() < count && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(200)).forEach(record -> {
                    if (key.equals(record.key())) {
                        found.add(record);
                    }
                });
            }
        }
        return found;
    }

    @Test
    void aLotsEventsArriveOnItsTopicInTheOrderTheyHappened() throws Exception {
        String auction = openLot("stream");
        String anna = tokenFor("stream.anna@nexbid.com", "Anna Stream", RoleName.BUYER);
        String ben = tokenFor("stream.ben@nexbid.com", "Ben Stream", RoleName.BUYER);
        UUID annaId = idOf(anna);
        UUID benId = idOf(ben);

        bid(anna, auction, 10_000_000L);
        bid(ben, auction, 10_500_000L);
        bid(anna, auction, 11_000_000L);

        List<ConsumerRecord<String, String>> got = read("nexbid.auctions", auction, 5);

        assertThat(got).extracting(EventHeaders::typeOf).containsExactly(
                "BidPlacedEvent", "BidPlacedEvent", "OutbidEvent", "BidPlacedEvent", "OutbidEvent");

        // EN: The event names who bid, which the public socket message never does.
        // VI: Sự kiện có ghi ai trả giá, điều mà bản tin công khai qua socket không bao giờ có.
        List<JsonNode> bids = got.stream().filter(record -> "BidPlacedEvent".equals(EventHeaders.typeOf(record)))
                .map(record -> objectMapper.readTree(record.value())).toList();
        assertThat(bids).extracting(bid -> bid.get("bidderId").asString())
                .containsExactly(annaId.toString(), benId.toString(), annaId.toString());
        assertThat(bids).extracting(bid -> bid.get("placed").get("bid").get("amount").decimalValue().longValue())
                .containsExactly(10_000_000L, 10_500_000L, 11_000_000L);

        assertThat(objectMapper.readTree(got.get(2).value()).get("outbid").get(0).asString()).isEqualTo(annaId.toString());
        assertThat(objectMapper.readTree(got.get(4).value()).get("outbid").get(0).asString()).isEqualTo(benId.toString());
    }

    private void freezeKafka() {
        kafka.getDockerClient().pauseContainerCmd(kafka.getContainerId()).exec();
    }

    private void thawKafka() {
        kafka.getDockerClient().unpauseContainerCmd(kafka.getContainerId()).exec();
    }

    @Test
    void bidsGoThroughWhileKafkaIsDownAndTheirNoticesArriveOnceItIsBack(CapturedOutput output) throws Exception {
        String auction = openLot("outage");
        String first = tokenFor("outage.first@nexbid.com", "First Bidder", RoleName.BUYER);
        String second = tokenFor("outage.second@nexbid.com", "Second Bidder", RoleName.BUYER);
        UUID firstId = idOf(first);
        bid(first, auction, 10_000_000L);
        await().atMost(Duration.ofSeconds(10)).until(() -> waitingInOutbox() == 0);

        freezeKafka();
        try {
            long start = System.nanoTime();
            bid(second, auction, 11_000_000L);
            // EN: The bid never waits on Kafka. / VI: Lượt trả giá không bao giờ phải chờ Kafka.
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));

            // EN: Its events are stuck in the outbox, not lost, and the relay says why.
            // VI: Sự kiện của nó kẹt lại trong outbox chứ không mất, và relay ghi rõ lý do.
            await().atMost(Duration.ofSeconds(20))
                    .until(() -> output.getOut().contains("Kafka unavailable, events wait in the outbox"));
            assertThat(waitingInOutbox()).isGreaterThanOrEqualTo(2);
            assertThat(outbidNotices(firstId)).isZero();
        } finally {
            thawKafka();
        }

        await().atMost(Duration.ofSeconds(30)).until(() -> outbidNotices(firstId) == 1);
        await().atMost(Duration.ofSeconds(30)).until(() -> waitingInOutbox() == 0);

        // EN: The frozen broker had in fact kept the first attempt, so the relay's retry sent the events a
        //     second time. The consumer recognises them: still one notice.
        // VI: Broker bị đóng băng thật ra vẫn giữ lần gửi đầu, nên lần thử lại của relay đã gửi sự kiện lần
        //     thứ hai. Consumer nhận ra chúng: vẫn chỉ một thông báo.
        Thread.sleep(1500);
        assertThat(outbidNotices(firstId)).isEqualTo(1);
    }
}
