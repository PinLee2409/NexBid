package com.nexbid.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.auction.AuctionLifecycleEvent;
import com.nexbid.auction.AuctionStatus;
import com.nexbid.bid.BidPlacedEvent;
import com.nexbid.bid.BidView;
import com.nexbid.bid.PlacedBidView;
import com.nexbid.infrastructure.kafka.EventHeaders;
import com.nexbid.payment.PaymentEvents;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The Analytics Consumer (spec §19). Events are sent straight to Kafka stamped with an hour in the past,
 *     so each test reads a row nothing else in the suite writes to.
 * VI: Analytics Consumer (spec §19). Sự kiện được gửi thẳng vào Kafka với mốc giờ trong quá khứ, nên mỗi test
 *     đọc một dòng mà không gì khác trong bộ test ghi vào.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class AnalyticsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private ObjectMapper objectMapper;

    /** EN: A distinct hour in 2001 for each test. / VI: Mỗi test một giờ riêng trong năm 2001. */
    private static Instant hourIn2001(int hourOfYear) {
        return Instant.parse("2001-01-01T00:00:00Z").plus(hourOfYear, ChronoUnit.HOURS);
    }

    private void deliver(String topic, UUID lot, UUID eventId, Object event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, lot.toString(), objectMapper.writeValueAsString(event));
        record.headers().add(EventHeaders.ID, eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventHeaders.TYPE,
                EventHeaders.typeName(event.getClass()).getBytes(StandardCharsets.UTF_8));
        kafka.send(record).join();
    }

    private static BidPlacedEvent bidAt(UUID lot, Instant at, String amount) {
        BigDecimal price = new BigDecimal(amount);
        return new BidPlacedEvent(new PlacedBidView(
                new BidView(UUID.randomUUID(), lot, "ann***", false, price, at),
                price, 1, price.add(new BigDecimal("500000")), at.plus(Duration.ofHours(1)), at, true),
                UUID.randomUUID());
    }

    private static AuctionLifecycleEvent endedAt(UUID lot, Instant at, UUID winner) {
        return new AuctionLifecycleEvent(AuctionLifecycleEvent.ENDED, lot, AuctionStatus.ENDED,
                at.minus(Duration.ofDays(1)), at, new BigDecimal("12000000"), winner, winner);
    }

    private Map<String, Object> rowFor(Instant hour) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM analytics_hourly WHERE hour = ?",
                OffsetDateTime.ofInstant(hour, ZoneOffset.UTC));
        return rows.isEmpty() ? null : rows.getFirst();
    }

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

    /**
     * EN: A real lot and buyer: the notification consumer reads the same events and writes notices that point
     *     at both, so they must exist. Only the timestamps are made up.
     * VI: Lô và người mua có thật: notification consumer đọc cùng các sự kiện và ghi thông báo trỏ tới cả hai, nên
     *     chúng phải tồn tại. Chỉ mốc thời gian là bịa ra.
     */
    private UUID[] realLotAndBuyer() throws Exception {
        String seller = tokenFor("analytics.seller@nexbid.com", "Analytics Seller", RoleName.SELLER);
        String admin = tokenFor("analytics.lot.admin@nexbid.com", "Analytics Lot Admin", RoleName.ADMIN);
        tokenFor("analytics.winner@nexbid.com", "Analytics Winner", RoleName.BUYER);
        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();
        String product = objectMapper.readTree(mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Counted lot","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"GOOD","publishNow":true}
                                """.formatted(categoryId)))
                .andReturn().getResponse().getContentAsString()).get("data").get("id").asString();
        String auction = objectMapper.readTree(mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(product, Instant.now().plus(Duration.ofHours(1)),
                                Instant.now().plus(Duration.ofHours(2)))))
                .andReturn().getResponse().getContentAsString()).get("data").get("id").asString();
        return new UUID[] { UUID.fromString(auction),
                users.findByEmail("analytics.winner@nexbid.com").orElseThrow().id() };
    }

    @Test
    void eachEventIsCountedIntoTheHourItHappenedIn() throws Exception {
        Instant hour = hourIn2001(10);
        Instant at = hour.plus(Duration.ofMinutes(25));
        UUID[] real = realLotAndBuyer();
        UUID sold = real[0];
        UUID winner = real[1];
        UUID unsold = UUID.randomUUID();

        deliver("nexbid.auctions", sold, UUID.randomUUID(), bidAt(sold, at, "10000000"));
        deliver("nexbid.auctions", sold, UUID.randomUUID(), bidAt(sold, at.plusSeconds(60), "10500000"));
        deliver("nexbid.auctions", sold, UUID.randomUUID(), endedAt(sold, at.plusSeconds(120), winner));
        deliver("nexbid.auctions", unsold, UUID.randomUUID(), endedAt(unsold, at.plusSeconds(130), null));
        deliver("nexbid.payments", sold, UUID.randomUUID(), new PaymentEvents.Succeeded(
                UUID.randomUUID(), sold, winner, new BigDecimal("10500000"), at.plusSeconds(300)));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Map<String, Object> row = rowFor(hour);
            assertThat(row).isNotNull();
            assertThat(row.get("bids")).isEqualTo(2);
            assertThat(row.get("auctions_ended")).isEqualTo(2);
            assertThat(row.get("auctions_sold")).isEqualTo(1);
            assertThat(row.get("payments")).isEqualTo(1);
            assertThat((BigDecimal) row.get("revenue")).isEqualByComparingTo("10500000");
        });
    }

    @Test
    void anEventDeliveredTwiceIsCountedOnce() throws Exception {
        Instant hour = hourIn2001(20);
        UUID lot = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        BidPlacedEvent bid = bidAt(lot, hour.plusSeconds(10), "10000000");

        deliver("nexbid.auctions", lot, eventId, bid);
        deliver("nexbid.auctions", lot, eventId, bid);
        // EN: A different event behind both, marking the point they must have been read by.
        // VI: Một sự kiện khác nằm sau cả hai, đánh dấu thời điểm chắc chắn chúng đã được đọc.
        deliver("nexbid.auctions", lot, UUID.randomUUID(), endedAt(lot, hour.plusSeconds(20), null));

        await().atMost(Duration.ofSeconds(15)).until(() -> {
            Map<String, Object> row = rowFor(hour);
            return row != null && Integer.valueOf(1).equals(row.get("auctions_ended"));
        });
        assertThat(rowFor(hour).get("bids")).isEqualTo(1);
    }

    @Test
    void theAdminReportFillsQuietHoursAndIsForAdminsOnly() throws Exception {
        String admin = tokenFor("analytics.admin@nexbid.com", "Analytics Admin", RoleName.ADMIN);
        String buyer = tokenFor("analytics.buyer@nexbid.com", "Analytics Buyer", RoleName.BUYER);

        mockMvc.perform(get("/api/admin/analytics").param("hours", "3").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hours.length()").value(3))
                .andExpect(jsonPath("$.data.hours[2].hour").value(
                        Instant.now().truncatedTo(ChronoUnit.HOURS).toString()))
                .andExpect(jsonPath("$.data.totals.bids").isNumber());

        mockMvc.perform(get("/api/admin/analytics").param("hours", "100000").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.hours.length()").value(AnalyticsService.MAX_HOURS));

        mockMvc.perform(get("/api/admin/analytics").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/analytics")).andExpect(status().isUnauthorized());
    }
}
