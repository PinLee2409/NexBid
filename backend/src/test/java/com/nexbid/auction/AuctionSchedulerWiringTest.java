package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The scheduler really runs (guide §25). Every other test calls the rule directly, which proves the
 *     rule but not that anything calls it — a missing @EnableScheduling would leave all of them green.
 * VI: Scheduler thật sự chạy (guide §25). Mọi test khác gọi thẳng luật, chứng minh được luật nhưng không
 *     chứng minh có ai gọi nó — thiếu @EnableScheduling thì tất cả vẫn xanh.
 */
@SpringBootTest(properties = {
        "nexbid.scheduler.enabled=true",
        "nexbid.scheduler.interval=200"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class AuctionSchedulerWiringTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email, String name, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","email":"%s","password":"supersecret"}
                        """.formatted(name, email)));

        users.grantRole(email, role);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    @Test
    void aDueLotOpensWithoutAnyoneAskingForIt() throws Exception {
        String seller = tokenFor("wire.seller@nexbid.com", "Wire Seller", RoleName.SELLER);
        String admin = tokenFor("wire.admin@nexbid.com", "Wire Admin", RoleName.ADMIN);

        String categories = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();

        String productBody = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Clockwork lot","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(categoryId)))
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();

        String auctionBody = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId,
                                Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();
        String auction = objectMapper.readTree(auctionBody).get("data").get("id").asString();

        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit")
                .header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auction + "/approve")
                .header("Authorization", "Bearer " + admin));

        jdbc.update("UPDATE auctions SET start_time = now() - interval '1 second' WHERE id = ?::uuid", auction);

        // EN: No call to the service here. Only the background thread can move it.
        // VI: Không gọi service ở đây. Chỉ luồng chạy nền mới có thể đẩy nó đi.
        Instant deadline = Instant.now().plusSeconds(5);
        String status = "SCHEDULED";

        while (Instant.now().isBefore(deadline) && !"ACTIVE".equals(status)) {
            Thread.sleep(100);
            status = jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, auction);
        }

        assertThat(status).isEqualTo("ACTIVE");
    }
}
