package com.nexbid.trust;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

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
 * EN: A bidder sharing a device with the seller is flagged for an admin, who may clear the pair until it bids again.
 * VI: Người trả giá dùng chung thiết bị với người bán bị đưa cho admin xem, admin có thể bỏ nghi vấn tới khi họ trả giá lại.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class ShillSignalsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Insight User","email":"%s","password":"supersecret"}
                """.formatted(email)));
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"supersecret"}
                """.formatted(email))).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    /** EN: An approved, open lot in the given category. / VI: Một lô đã duyệt, đang mở, trong danh mục cho trước. */
    /** EN: A category of the test's own, so other tests' lots stay out of it. / VI: Một danh mục riêng của test, để lô của test khác không lẫn vào. */
    private String category(String admin, String name) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    private String openLot(String seller, String admin, String name, String categoryId) throws Exception {
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","description":"A long description of the item.",
                         "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                        """.formatted(name, categoryId))).andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();
        String auctionBody = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"productId":"%s","startingPrice":1000000,"minimumIncrement":100000,
                         "startTime":"%s","endTime":"%s"}
                        """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)),
                        Instant.now().plus(Duration.ofHours(4))))).andReturn().getResponse().getContentAsString();
        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auctionId);
        return auctionId;
    }

    @Test
    void aBidderOnTheSellersDeviceIsFlaggedUntilAnAdminClearsThePair() throws Exception {
        String seller = tokenFor("sh.seller@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("sh.admin@nexbid.com", RoleName.ADMIN);
        String shill = tokenFor("sh.shill@nexbid.com", RoleName.BUYER);
        String honest = tokenFor("sh.honest@nexbid.com", RoleName.BUYER);
        String lot = openLot(seller, admin, "Shill lot", category(admin, "Shill watches"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + seller)
                .header("X-Device-Id", "device-shared-1234")).andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + shill)
                .header("X-Device-Id", "device-shared-1234")).andExpect(status().isOk());
        // EN: A malformed id is ignored. / VI: Mã sai định dạng bị bỏ qua.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + honest)
                .header("X-Device-Id", "<script>")).andExpect(status().isOk());
        bid(shill, lot, 1000000);
        bid(honest, lot, 1100000);

        mockMvc.perform(get("/api/admin/shill-suspects").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.bidder.email == 'sh.shill@nexbid.com')].signals[*]",
                        org.hamcrest.Matchers.hasItems("SHARED_DEVICE", "NEW_ACCOUNT")))
                .andExpect(jsonPath("$.data[?(@.bidder.email == 'sh.shill@nexbid.com')].score",
                        org.hamcrest.Matchers.contains(60)))
                .andExpect(jsonPath("$.data[?(@.bidder.email == 'sh.honest@nexbid.com')]").isEmpty());
        mockMvc.perform(get("/api/admin/shill-suspects").header("Authorization", "Bearer " + seller))
                .andExpect(status().isForbidden());

        String sellerId = userId("sh.seller@nexbid.com");
        String shillId = userId("sh.shill@nexbid.com");
        mockMvc.perform(post("/api/admin/shill-suspects/clear").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sellerId\":\"%s\",\"bidderId\":\"%s\",\"note\":\"Same office\"}"
                                .formatted(sellerId, shillId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/shill-suspects").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data[?(@.bidder.email == 'sh.shill@nexbid.com')]").isEmpty());
        assertThat(jdbc.queryForObject("SELECT new_value FROM audit_logs WHERE action = 'SHILL_PAIR_CLEARED' "
                + "AND entity_id = ?::uuid", String.class, shillId)).isEqualTo("seller " + sellerId + ": Same office");

        // EN: Bidding on the seller again brings the pair back. / VI: Trả giá lô của người bán này lần nữa thì cặp hiện lại.
        jdbc.update("UPDATE shill_reviews SET decided_at = decided_at - interval '1 minute'");
        bid(shill, lot, 1200000);
        mockMvc.perform(get("/api/admin/shill-suspects").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data[?(@.bidder.email == 'sh.shill@nexbid.com')]").isNotEmpty());
    }

    private void bid(String token, String auctionId, long amount) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    private String userId(String email) {
        return jdbc.queryForObject("SELECT id::text FROM users WHERE email = ?", String.class, email);
    }
}
