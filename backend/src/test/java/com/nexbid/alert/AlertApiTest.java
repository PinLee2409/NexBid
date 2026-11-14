package com.nexbid.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
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
 * EN: Saved searches and followed sellers hear of each newly approved lot that fits, once, and get it in a daily email.
 * VI: Bộ lọc đã lưu và người bán đang theo dõi được báo về mỗi lô vừa duyệt mà khớp, một lần, và nhận trong email hằng ngày.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class AlertApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlertService alerts;

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

    private String firstCategory(String field) throws Exception {
        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(categories).get("data").get(0).get(field).asString();
    }

    /** EN: Lists, submits and approves a lot. / VI: Đăng, gửi duyệt và duyệt một lô. */
    private String approvedLot(String seller, String admin, String name, long startingPrice) throws Exception {
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"%s","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(name, firstCategory("id"))))
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();
        String auctionBody = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":%d,"minimumIncrement":100000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId, startingPrice, Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();
        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        return auctionId;
    }

    private ResultActions saveSearch(String token, String json) throws Exception {
        return mockMvc.perform(post("/api/alerts/searches").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private List<String> alertsTo(UUID userId) {
        return jdbc.queryForList("SELECT message FROM notifications WHERE user_id = ? AND type = 'LOT_ALERT'",
                String.class, userId);
    }

    private List<String> digestsTo(UUID userId) {
        return jdbc.queryForList("SELECT html FROM email_outbox WHERE user_id = ? AND kind = 'LOT_DIGEST'", String.class,
                userId);
    }

    @Test
    void searchesAndFollowsHearOfNewLotsOnceAndGetThemInADailyEmail() throws Exception {
        String seller = tokenFor("al.seller@nexbid.com", "Alva Seller", RoleName.SELLER);
        String admin = tokenFor("al.admin@nexbid.com", "Alva Admin", RoleName.ADMIN);
        String searcher = tokenFor("al.search@nexbid.com", "Sam Searcher", RoleName.BUYER);
        String follower = tokenFor("al.follow@nexbid.com", "Fay Follower", RoleName.BUYER);
        String other = tokenFor("al.other@nexbid.com", "Otto Other", RoleName.BUYER);
        UUID sellerId = idOf("al.seller@nexbid.com");

        saveSearch(searcher, """
                {"q":"  presage ","category":["%s"],"condition":["LIKE_NEW","NEW"],"maxPrice":5000000}
                """.formatted(firstCategory("slug")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.q").value("presage"))
                .andExpect(jsonPath("$.data.condition[0]").value("LIKE_NEW"));
        saveSearch(other, "{\"q\":\"rolex\"}").andExpect(status().isCreated());
        // EN: Too dear for the searcher's price cap. / VI: Quá giá trần của người tìm.
        saveSearch(other, "{\"minPrice\":90000000}").andExpect(status().isCreated());
        mockMvc.perform(put("/api/alerts/sellers/" + sellerId).header("Authorization", "Bearer " + follower))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.following").value(true));
        // EN: The seller's own search never fires on their own lot. / VI: Bộ lọc của chính người bán không báo lô của họ.
        saveSearch(seller, "{\"q\":\"presage\"}").andExpect(status().isCreated());

        String seiko = approvedLot(seller, admin, "Seiko Presage SRPD37", 2_000_000);
        approvedLot(seller, admin, "Leica M6", 3_000_000);

        assertThat(alertsTo(idOf("al.search@nexbid.com"))).containsExactly("Seiko Presage SRPD37");
        assertThat(alertsTo(idOf("al.follow@nexbid.com")))
                .containsExactlyInAnyOrder("Seiko Presage SRPD37", "Leica M6");
        assertThat(alertsTo(idOf("al.other@nexbid.com"))).isEmpty();
        assertThat(alertsTo(sellerId)).isEmpty();

        // EN: Approval is the only trigger, and a second pass changes nothing. / VI: Chỉ lần duyệt mới kích hoạt, và chạy lại không đổi gì.
        assertThat(alerts.match(UUID.fromString(seiko), Instant.now())).isZero();

        alerts.sendDigests(Instant.now());
        assertThat(digestsTo(idOf("al.search@nexbid.com"))).singleElement()
                .satisfies(html -> assertThat(html).contains("Seiko Presage SRPD37").contains("presage")
                        .doesNotContain("Leica M6"));
        assertThat(digestsTo(idOf("al.follow@nexbid.com"))).singleElement()
                .satisfies(html -> assertThat(html).contains("Seiko Presage SRPD37").contains("Leica M6")
                        .contains("alv***"));
        alerts.sendDigests(Instant.now());
        assertThat(digestsTo(idOf("al.follow@nexbid.com"))).hasSize(1);

        // EN: The email's own one-click link turns the digest off. / VI: Link một chạm trong email tắt email tổng hợp.
        String oneClick = jdbc.queryForObject(
                "SELECT unsubscribe_url FROM email_outbox WHERE user_id = ? AND kind = 'LOT_DIGEST'", String.class,
                idOf("al.follow@nexbid.com"));
        URI link = URI.create(oneClick);
        mockMvc.perform(post(link.getPath() + "?" + link.getRawQuery())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.digest").value(false));
        mockMvc.perform(get("/api/users/me/email-preferences").header("Authorization", "Bearer " + follower))
                .andExpect(jsonPath("$.data.outbid").value(true)).andExpect(jsonPath("$.data.digest").value(false));

        approvedLot(seller, admin, "Fuji X100V", 2_500_000);
        alerts.sendDigests(Instant.now());
        assertThat(alertsTo(idOf("al.follow@nexbid.com"))).hasSize(3);
        assertThat(digestsTo(idOf("al.follow@nexbid.com"))).hasSize(1);

        mockMvc.perform(delete("/api/alerts/sellers/" + sellerId).header("Authorization", "Bearer " + follower))
                .andExpect(jsonPath("$.data.following").value(false));
        mockMvc.perform(get("/api/alerts/sellers").header("Authorization", "Bearer " + follower))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void savedSearchesAndFollowsHaveTheirLimits() throws Exception {
        String buyer = tokenFor("al.limits@nexbid.com", "Lim Buyer", RoleName.BUYER);
        String stranger = tokenFor("al.stranger@nexbid.com", "Stan Stranger", RoleName.BUYER);
        String seller = tokenFor("al.seller2@nexbid.com", "Sol Seller", RoleName.SELLER);

        saveSearch(buyer, "{}").andExpect(status().isBadRequest());
        saveSearch(buyer, "{\"q\":\"   \"}").andExpect(status().isBadRequest());
        saveSearch(buyer, "{\"minPrice\":5,\"maxPrice\":1}").andExpect(status().isBadRequest());
        saveSearch(buyer, "{\"category\":[\"Bad Slug\"]}").andExpect(status().isBadRequest());

        String first = objectMapper.readTree(saveSearch(buyer, "{\"q\":\"camera\",\"minPrice\":100}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("data").get("id").asString();
        // EN: The same filters again come back as the same search. / VI: Cùng bộ lọc lần nữa trả về đúng bộ lọc cũ.
        saveSearch(buyer, "{\"q\":\" camera\",\"minPrice\":100.00}").andExpect(jsonPath("$.data.id").value(first));
        for (int i = 1; i < AlertService.SEARCH_LIMIT; i++) {
            saveSearch(buyer, "{\"q\":\"lens " + i + "\"}").andExpect(status().isCreated());
        }
        saveSearch(buyer, "{\"q\":\"one too many\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAVED_SEARCH_LIMIT"));
        mockMvc.perform(get("/api/alerts/searches").header("Authorization", "Bearer " + buyer))
                .andExpect(jsonPath("$.data.length()").value(AlertService.SEARCH_LIMIT));

        // EN: Someone else's search is not found. / VI: Bộ lọc của người khác thì không tìm thấy.
        mockMvc.perform(delete("/api/alerts/searches/" + first).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/alerts/searches/" + first).header("Authorization", "Bearer " + buyer))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/alerts/sellers/" + idOf("al.seller2@nexbid.com")).header("Authorization", "Bearer " + seller))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FOLLOW_NOT_ALLOWED"));
        mockMvc.perform(put("/api/alerts/sellers/" + idOf("al.stranger@nexbid.com")).header("Authorization", "Bearer " + buyer))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/alerts/sellers/" + idOf("al.seller2@nexbid.com")).header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/alerts/sellers/" + idOf("al.seller2@nexbid.com")).header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/alerts/sellers/" + idOf("al.seller2@nexbid.com")).header("Authorization", "Bearer " + buyer))
                .andExpect(jsonPath("$.data.following").value(true));
        mockMvc.perform(get("/api/alerts/sellers").header("Authorization", "Bearer " + buyer))
                .andExpect(jsonPath("$.data[0].displayName").value("sol***"));

        mockMvc.perform(get("/api/alerts/searches")).andExpect(status().isUnauthorized());
    }
}
