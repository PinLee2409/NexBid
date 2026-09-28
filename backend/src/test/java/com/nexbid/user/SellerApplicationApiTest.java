package com.nexbid.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.nexbid.support.TestInfrastructure;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * EN: Becoming a seller (spec §7.1): a buyer asks, an admin approves or rejects, a rejected buyer asks again.
 * VI: Trở thành người bán (spec §7.1): người mua gửi yêu cầu, admin duyệt hoặc từ chối, người bị từ chối gửi lại.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class SellerApplicationApiTest {

    private static final Pattern REFRESH_COOKIE = Pattern.compile("nexbid_refresh=([^;]*)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private record Session(UUID id, String token, String refresh) {
    }

    private Session signUp(String tag, RoleName role) throws Exception {
        String email = "seller-app." + tag + "." + UUID.randomUUID() + "@nexbid.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Applicant %s","email":"%s","password":"supersecret"}
                """.formatted(tag, email))).andExpect(status().isCreated());
        users.grantRole(email, role);
        MvcResult login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"supersecret"}
                        """.formatted(email)))
                .andExpect(status().isOk()).andReturn();
        Matcher cookie = REFRESH_COOKIE.matcher(login.getResponse().getHeader(HttpHeaders.SET_COOKIE));
        assertThat(cookie.find()).isTrue();
        return new Session(users.findByEmail(email).orElseThrow().id(), tokenOf(login), cookie.group(1));
    }

    private String tokenOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("data").get("accessToken").asString();
    }

    private MvcResult apply(Session buyer, String note) throws Exception {
        return mockMvc.perform(post("/api/users/me/seller-application").header("Authorization", "Bearer " + buyer.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(java.util.Map.of("note", note))))
                .andReturn();
    }

    private String applicationIdOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).get("data").get("id").asString();
    }

    private long notices(UUID userId, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?",
                Long.class, userId, type);
    }

    private long audits(String applicationId, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?::uuid AND action = ?",
                Long.class, applicationId, action);
    }

    @Test
    void anApprovedBuyerCanSellOnceTheirSessionRenews() throws Exception {
        Session buyer = signUp("approved", RoleName.BUYER);
        Session admin = signUp("admin", RoleName.ADMIN);

        MvcResult sent = apply(buyer, "Vintage cameras and lenses, about ten a month.");
        assertThat(sent.getResponse().getStatus()).isEqualTo(201);
        String id = applicationIdOf(sent);
        mockMvc.perform(get("/api/users/me/seller-application").header("Authorization", "Bearer " + buyer.token()))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.note").value("Vintage cameras and lenses, about ten a month."));

        // EN: One request waits at a time. / VI: Mỗi lúc chỉ một yêu cầu được chờ.
        MvcResult again = apply(buyer, "Another try");
        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(again.getResponse().getContentAsString()).contains("SELLER_APPLICATION_PENDING");

        mockMvc.perform(get("/api/admin/seller-applications").param("status", "PENDING")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + id + "')].fullName").value("Applicant approved"));

        mockMvc.perform(post("/api/admin/seller-applications/" + id + "/approve")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        assertThat(users.findById(buyer.id()).orElseThrow().roles()).contains(RoleName.BUYER, RoleName.SELLER);

        // EN: The old token still says BUYER; the renewed one carries the new role.
        // VI: Token cũ vẫn ghi BUYER; token được gia hạn mang vai trò mới.
        mockMvc.perform(get("/api/seller/products").header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isForbidden());
        MvcResult renewed = mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie("nexbid_refresh", buyer.refresh())))
                .andExpect(status().isOk()).andReturn();
        mockMvc.perform(get("/api/seller/products").header("Authorization", "Bearer " + tokenOf(renewed)))
                .andExpect(status().isOk());

        assertThat(notices(buyer.id(), "SELLER_APPROVED")).isEqualTo(1);
        assertThat(audits(id, "SELLER_APPLIED")).isEqualTo(1);
        assertThat(audits(id, "SELLER_APPROVED")).isEqualTo(1);

        // EN: Deciding twice, or asking once already a seller, is refused. / VI: Quyết định hai lần, hay gửi khi đã là người bán, đều bị từ chối.
        mockMvc.perform(post("/api/admin/seller-applications/" + id + "/reject")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Too late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELLER_APPLICATION_NOT_PENDING"));
        MvcResult asSeller = apply(buyer, "Once more");
        assertThat(asSeller.getResponse().getStatus()).isEqualTo(409);
        assertThat(asSeller.getResponse().getContentAsString()).contains("ALREADY_SELLER");
    }

    @Test
    void aRejectedBuyerHearsWhyAndMayAskAgain() throws Exception {
        Session buyer = signUp("rejected", RoleName.BUYER);
        Session admin = signUp("judge", RoleName.ADMIN);
        String first = applicationIdOf(apply(buyer, "Stuff"));

        mockMvc.perform(post("/api/admin/seller-applications/" + first + "/reject")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/admin/seller-applications/" + first + "/reject")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Say what kind of items you will list.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(users.findById(buyer.id()).orElseThrow().roles()).doesNotContain(RoleName.SELLER);
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE user_id = ? AND type = 'SELLER_REJECTED'",
                String.class, buyer.id())).isEqualTo("Say what kind of items you will list.");
        assertThat(audits(first, "SELLER_REJECTED")).isEqualTo(1);
        mockMvc.perform(get("/api/users/me/seller-application").header("Authorization", "Bearer " + buyer.token()))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Say what kind of items you will list."));

        MvcResult second = apply(buyer, "Hand-made leather bags.");
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        mockMvc.perform(get("/api/users/me/seller-application").header("Authorization", "Bearer " + buyer.token()))
                .andExpect(jsonPath("$.data.id").value(applicationIdOf(second)))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void twoTabsSendingAtOnceLeaveOneRequest() throws Exception {
        Session buyer = signUp("tabs", RoleName.BUYER);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> answers = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                answers.add(pool.submit(() -> {
                    start.await();
                    return apply(buyer, "Books").getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> answer : answers) {
                statuses.add(answer.get());
            }
            assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM seller_applications WHERE user_id = ?",
                Long.class, buyer.id())).isEqualTo(1);
    }

    @Test
    void aNoteIsRequiredAndOnlyAdminsDecide() throws Exception {
        Session buyer = signUp("rules", RoleName.BUYER);
        MvcResult blank = apply(buyer, "   ");
        assertThat(blank.getResponse().getStatus()).isEqualTo(400);

        String id = applicationIdOf(apply(buyer, "Watches"));
        mockMvc.perform(get("/api/admin/seller-applications").header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/seller-applications/" + id + "/approve")
                        .header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/me/seller-application")).andExpect(status().isUnauthorized());

        Session admin = signUp("missing", RoleName.ADMIN);
        mockMvc.perform(post("/api/admin/seller-applications/" + UUID.randomUUID() + "/approve")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SELLER_APPLICATION_NOT_FOUND"));
    }

    @Test
    void someoneWhoNeverAskedHasNoRequest() throws Exception {
        Session buyer = signUp("never", RoleName.BUYER);
        mockMvc.perform(get("/api/users/me/seller-application").header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
