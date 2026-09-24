package com.nexbid.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.auction.AuctionService;
import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: A broken notification never costs anyone a bid or a result. Every notice here fails to save, and
 *     the bidding and the closing must not notice.
 * VI: Thông báo hỏng không bao giờ làm ai mất lượt trả giá hay kết quả. Ở đây mọi thông báo đều lưu hỏng,
 *     và việc trả giá lẫn đóng phiên không được bị ảnh hưởng.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class NotificationFailureIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionService auctions;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private NotificationService notifications;

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
    void biddingAndClosingCarryOnWhenNoticesCannotBeSaved() throws Exception {
        doThrow(new IllegalStateException("notification store is down"))
                .when(notifications).notify(any(), any(), any(), any(), any(), any());

        String seller = tokenFor("iso.seller@nexbid.com", "Iso Seller", RoleName.SELLER);
        String admin = tokenFor("iso.admin@nexbid.com", "Iso Admin", RoleName.ADMIN);
        String pin = tokenFor("iso.pin@nexbid.com", "Pin Iso", RoleName.BUYER);
        String alex = tokenFor("iso.alex@nexbid.com", "Alex Iso", RoleName.BUYER);

        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();
        String productBody = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Resilient lot","description":"A long description of the item.",
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
        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auction + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' "
                + "WHERE id = ?::uuid", auction);

        mockMvc.perform(post("/api/auctions/" + auction + "/bids")
                        .header("Authorization", "Bearer " + pin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000000}"))
                .andExpect(status().isCreated());

        // EN: This bid would outbid pin — the notice fails, the bid must still be accepted.
        // VI: Lượt này vượt giá pin — thông báo hỏng, lượt trả giá vẫn phải được nhận.
        mockMvc.perform(post("/api/auctions/" + auction + "/bids")
                        .header("Authorization", "Bearer " + alex)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10500000}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currentPrice").value(10500000));

        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", auction);
        assertThat(auctions.endDueAuctions(Instant.now(), 200)).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, auction))
                .isEqualTo("ENDED");
        assertThat(jdbc.queryForObject("SELECT winner_id IS NOT NULL FROM auctions WHERE id = ?::uuid",
                Boolean.class, auction)).isTrue();
        // EN: And the failures really happened — both the outbid notice and the win notice were attempted.
        // VI: Và lỗi thật sự đã xảy ra — cả thông báo bị vượt giá lẫn thông báo thắng đều đã được thử ghi.
        verify(notifications, timeout(10_000).atLeast(2)).notify(any(), any(), any(), any(), any(), any());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class)).isZero();
    }
}
