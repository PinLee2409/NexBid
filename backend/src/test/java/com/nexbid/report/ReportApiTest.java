package com.nexbid.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: People report lots; admins dismiss reports or take lots down; blocking a seller takes their lots down too.
 * VI: Người dùng báo cáo lô; admin bỏ qua báo cáo hoặc gỡ lô; khoá người bán cũng gỡ các lô của họ.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class ReportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private UUID idOf(String email) {
        return users.findByEmail(email).orElseThrow().id();
    }

    /** EN: A live lot of the given seller. / VI: Một lô đang mở của người bán đã cho. */
    private String liveLot(String seller, String admin, String name) throws Exception {
        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"%s","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(name, categoryId)))
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();
        String auctionBody = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":1000000,"minimumIncrement":100000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();
        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auctionId);
        return auctionId;
    }

    private String idFrom(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data").get("id")
                .asString();
    }

    private List<String> noticesTo(UUID userId) {
        return jdbc.queryForList("SELECT type FROM notifications WHERE user_id = ?", String.class, userId);
    }

    private List<String> mailsTo(UUID userId) {
        return jdbc.queryForList("SELECT kind FROM email_outbox WHERE user_id = ?", String.class, userId);
    }

    private ResultActions report(String token, String auctionId, String reason, String details) throws Exception {
        var request = post("/api/auctions/" + auctionId + "/reports").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"%s\",\"details\":\"%s\"}".formatted(reason, details));
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions decide(String admin, String reportId, String action, String body) throws Exception {
        return mockMvc.perform(post("/api/admin/reports/" + reportId + "/" + action)
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String statusOf(String table, String id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?::uuid", String.class, id);
    }

    @Test
    void aReportIsDismissedOrTheLotIsTakenDown() throws Exception {
        String seller = tokenFor("rp.seller@nexbid.com", "Rhea Seller", RoleName.SELLER);
        String admin = tokenFor("rp.admin@nexbid.com", "Rp Admin", RoleName.ADMIN);
        String first = tokenFor("rp.first@nexbid.com", "First Reporter", RoleName.BUYER);
        String second = tokenFor("rp.second@nexbid.com", "Second Reporter", RoleName.BUYER);
        String auctionId = liveLot(seller, admin, "Rolex that looks wrong");
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids").header("Authorization", "Bearer " + second)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000000}")).andExpect(status().isCreated());

        report(null, auctionId, "COUNTERFEIT", "The dial font is wrong.").andExpect(status().isUnauthorized());
        report(seller, auctionId, "COUNTERFEIT", "Reporting my own lot here.").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_ALLOWED"));
        report(first, auctionId, "COUNTERFEIT", "short").andExpect(status().isBadRequest());
        report(first, auctionId, "NOPE", "The dial font is wrong.").andExpect(status().isBadRequest());

        String firstReport = idFrom(report(first, auctionId, "COUNTERFEIT", "The dial font is wrong.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reporter").value("fir***"))
                .andExpect(jsonPath("$.data.status").value("OPEN")));
        report(first, auctionId, "OTHER", "Reporting the same lot again.").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_DUPLICATE"));
        String secondReport = idFrom(report(second, auctionId, "NOT_AS_DESCRIBED", "Photos are from a catalogue.")
                .andExpect(status().isCreated()));

        mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + first))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/reports").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data[?(@.id == '" + firstReport + "')].auction.product.name")
                        .value("Rolex that looks wrong"));

        // EN: One report is dismissed; the lot stays. / VI: Một báo cáo bị bỏ qua; lô vẫn giữ.
        decide(admin, secondReport, "dismiss", "{\"note\":\"The photos are the seller's own.\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DISMISSED"));
        decide(admin, secondReport, "dismiss", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_CLOSED"));
        assertThat(statusOf("auctions", auctionId)).isEqualTo("ACTIVE");
        assertThat(noticesTo(idOf("rp.second@nexbid.com"))).contains("REPORT_RESOLVED");

        // EN: The other is upheld: the lot comes down. / VI: Báo cáo còn lại được chấp nhận: lô bị gỡ.
        decide(admin, firstReport, "take-down", "{\"reason\":\" \"}").andExpect(status().isBadRequest());
        decide(admin, firstReport, "take-down", "{\"reason\":\"Counterfeit dial confirmed.\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(1));
        assertThat(statusOf("auctions", auctionId)).isEqualTo("REMOVED");
        assertThat(statusOf("lot_reports", firstReport)).isEqualTo("REMOVED");
        assertThat(jdbc.queryForObject(
                "SELECT p.status FROM products p JOIN auctions a ON a.product_id = p.id WHERE a.id = ?::uuid",
                String.class, auctionId)).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT rejection_reason FROM auctions WHERE id = ?::uuid", String.class,
                auctionId)).isEqualTo("Counterfeit dial confirmed.");
        UUID sellerId = idOf("rp.seller@nexbid.com");
        assertThat(noticesTo(sellerId)).contains("LOT_REMOVED");
        assertThat(mailsTo(sellerId)).contains("LOT_REMOVED");
        assertThat(noticesTo(idOf("rp.second@nexbid.com"))).contains("AUCTION_CANCELLED");
        assertThat(noticesTo(idOf("rp.first@nexbid.com"))).contains("REPORT_RESOLVED");
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE entity_id = ?::uuid", String.class,
                auctionId)).contains("AUCTION_REMOVED");

        // EN: Gone from public view, and nobody can bid. / VI: Biến khỏi trang công khai, và không ai trả giá được.
        mockMvc.perform(get("/api/auctions/" + auctionId)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids").header("Authorization", "Bearer " + second)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":2000000}"))
                .andExpect(status().is4xxClientError());
        report(first, auctionId, "OTHER", "Reporting a lot already down.").andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/remove").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Again\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUCTION_NOT_REMOVABLE"));
    }

    @Test
    void blockingASellerTakesTheirOpenLotsDown() throws Exception {
        String seller = tokenFor("rp.blocked.seller@nexbid.com", "Blocked Seller", RoleName.SELLER);
        String admin = tokenFor("rp.blocked.admin@nexbid.com", "Blocked Admin", RoleName.ADMIN);
        String live = liveLot(seller, admin, "Lot of a seller about to be blocked");

        mockMvc.perform(patch("/api/admin/users/" + idOf("rp.blocked.seller@nexbid.com") + "/block")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":true}"))
                .andExpect(status().isOk());

        assertThat(statusOf("auctions", live)).isEqualTo("REMOVED");
        assertThat(jdbc.queryForObject("SELECT rejection_reason FROM auctions WHERE id = ?::uuid", String.class, live))
                .contains("suspended");
    }
}
