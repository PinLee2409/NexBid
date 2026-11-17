package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Sets sold as one lot, the auction calendar, and drafts scheduled and sent for approval together or at a set time.
 * VI: Bộ nhiều món bán chung một lô, lịch đấu giá, và lô nháp được lên lịch rồi gửi duyệt cùng lúc hoặc hẹn giờ.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class LotPlanningApiTest {

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LotBatch batch;

    private String tokenFor(String email, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Planner","email":"%s","password":"supersecret"}
                """.formatted(email)));
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"supersecret"}
                """.formatted(email))).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    private String categoryId() throws Exception {
        String body = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get(0).get("id").asString();
    }

    private String product(String seller, String name, String items) throws Exception {
        String body = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"%s","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"GOOD","publishNow":true%s}
                                """.formatted(name, categoryId(), items == null ? "" : ",\"items\":" + items)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    private String draft(String seller, String productId, Instant start, Instant end) throws Exception {
        String body = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":1000000,"minimumIncrement":100000,
                                 "startTime":"%s","endTime":"%s","freeShipping":true}
                                """.formatted(productId, start, end)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    @Test
    void aSetListsItsPiecesOnTheLotPageAndTheCalendarShowsItsDay() throws Exception {
        String seller = tokenFor("plan.seller.set@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("plan.admin.set@nexbid.com", RoleName.ADMIN);
        mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Bad set","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"GOOD","items":[{"name":"","quantity":0}]}
                                """.formatted(categoryId())))
                .andExpect(status().isBadRequest());
        String productId = product(seller, "Tea set", """
                [{"name":" Teacup ","quantity":6},{"name":"Teapot","quantity":1}]""");
        mockMvc.perform(get("/api/seller/products/" + productId).header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.data.items[0].name").value("Teacup"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(6));

        Instant start = Instant.now().plus(Duration.ofDays(3));
        String auctionId = draft(seller, productId, start, start.plus(Duration.ofDays(2)));
        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auctions/" + auctionId))
                .andExpect(jsonPath("$.data.product.items.length()").value(2))
                .andExpect(jsonPath("$.data.product.items[1].name").value("Teapot"))
                .andExpect(jsonPath("$.data.auction.freeShipping").value(true));

        LocalDate day = start.atZone(VIETNAM).toLocalDate();
        String calendar = mockMvc.perform(get("/api/auctions/calendar")
                        .param("from", day.toString()).param("to", day.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(calendar).contains(auctionId);
        String earlier = mockMvc.perform(get("/api/auctions/calendar")
                        .param("from", day.minusDays(2).toString()).param("to", day.minusDays(1).toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(earlier).doesNotContain(auctionId);
        mockMvc.perform(get("/api/auctions/calendar").param("from", day.toString()).param("to", day.minusDays(1).toString()))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void draftsAreScheduledTogetherAndSentForApprovalNowOrLater() throws Exception {
        String seller = tokenFor("plan.seller.batch@nexbid.com", RoleName.SELLER);
        String other = tokenFor("plan.seller.other@nexbid.com", RoleName.SELLER);
        Instant soon = Instant.now().plus(Duration.ofDays(1));
        String first = draft(seller, product(seller, "Batch one", null), soon, soon.plus(Duration.ofDays(1)));
        String second = draft(seller, product(seller, "Batch two", null), soon, soon.plus(Duration.ofDays(1)));
        String stranger = draft(other, product(other, "Not yours", null), soon, soon.plus(Duration.ofDays(1)));

        Instant opens = Instant.now().plus(Duration.ofDays(5)).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        Instant closes = opens.plus(Duration.ofDays(2));
        mockMvc.perform(post("/api/seller/auctions/batch").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"auctionIds":["%s","%s"],"startTime":"%s","endTime":"%s","submit":true,
                                 "submitAt":"%s"}
                                """.formatted(first, stranger, opens, closes, Instant.now().plus(Duration.ofHours(2)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].done").value(true))
                .andExpect(jsonPath("$.data[1].done").value(false));
        assertThat(jdbc.queryForObject("SELECT status || ' ' || (submit_at IS NOT NULL) FROM auctions WHERE id = ?::uuid",
                String.class, first)).isEqualTo("DRAFT true");
        assertThat(jdbc.queryForObject("SELECT start_time FROM auctions WHERE id = ?::uuid", java.sql.Timestamp.class,
                first).toInstant()).isEqualTo(opens);
        mockMvc.perform(get("/api/seller/auctions/" + first).header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.data.submitAt").isNotEmpty());

        // EN: Not yet due: nothing goes; once due, the scheduler sends it. / VI: Chưa tới giờ: không gửi gì; tới giờ thì bộ lập lịch gửi.
        assertThat(batch.submitDue(Instant.now(), 100)).isZero();
        jdbc.update("UPDATE auctions SET submit_at = now() - interval '1 minute' WHERE id = ?::uuid", first);
        assertThat(batch.submitDue(Instant.now(), 100)).isOne();
        assertThat(jdbc.queryForObject("SELECT status || ' ' || (submit_at IS NULL) FROM auctions WHERE id = ?::uuid",
                String.class, first)).isEqualTo("PENDING_APPROVAL true");

        // EN: Now, and a draft that is no longer a draft is reported. / VI: Gửi ngay, và lô không còn là nháp thì được báo.
        mockMvc.perform(post("/api/seller/auctions/batch").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"auctionIds":["%s","%s"],"submit":true}""".formatted(second, first)))
                .andExpect(jsonPath("$.data[0].done").value(true))
                .andExpect(jsonPath("$.data[1].done").value(false));
        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, second))
                .isEqualTo("PENDING_APPROVAL");

        mockMvc.perform(post("/api/seller/auctions/batch").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"auctionIds":["%s"],"startTime":"%s","submit":false}""".formatted(second, opens)))
                .andExpect(jsonPath("$.code").value("AUCTION_SCHEDULE_INVALID"));
        mockMvc.perform(post("/api/seller/auctions/batch").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"auctionIds":["%s"],"startTime":"%s","endTime":"%s","submit":true,"submitAt":"%s"}
                                """.formatted(second, opens, closes, opens.plus(Duration.ofHours(1)))))
                .andExpect(jsonPath("$.code").value("AUCTION_SCHEDULE_INVALID"));
        assertThat(List.of(first, second)).doesNotContain(stranger);
    }
}
