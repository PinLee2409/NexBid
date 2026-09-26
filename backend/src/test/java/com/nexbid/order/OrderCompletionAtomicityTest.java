package com.nexbid.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: "Paid" and "completed" never disagree. If the sale cannot be completed, the payment is not taken
 *     either, and the winner can simply try again.
 * VI: "Đã trả" và "đã hoàn tất" không bao giờ lệch nhau. Nếu không hoàn tất được giao dịch thì cũng không
 *     nhận thanh toán, và người thắng chỉ việc thử lại.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class OrderCompletionAtomicityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private AuctionService auctions;

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

    @Test
    void ifTheSaleCannotBeCompletedThePaymentIsNotTakenAndCanBeRetried() throws Exception {
        String seller = tokenFor("atomo.seller@nexbid.com", "Atomo Seller", RoleName.SELLER);
        String admin = tokenFor("atomo.admin@nexbid.com", "Atomo Admin", RoleName.ADMIN);
        String winner = tokenFor("atomo.winner@nexbid.com", "Atomo Winner", RoleName.BUYER);

        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Atomic sale","description":"A long description of the item.",
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
        mockMvc.perform(post("/api/auctions/" + auction + "/bids").header("Authorization", "Bearer " + winner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000000}"));
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", auction);
        auctions.endDueAuctions(Instant.now(), 200);
        String paymentId = jdbc.queryForObject("SELECT id::text FROM payments WHERE auction_id = ?::uuid",
                String.class, auction);

        // EN: The close sends its "you won" notice on another thread, which calls this same spy. Stubbing a
        //     spy while another thread is using it is a Mockito race, so let that notice land first.
        // VI: Việc đóng phiên gửi thông báo "bạn đã thắng" trên luồng khác, luồng đó gọi chính spy này. Stub
        //     một spy trong lúc luồng khác đang dùng nó là race của Mockito, nên chờ thông báo đó xong trước.
        await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE auction_id = ?::uuid AND type = 'AUCTION_WON'",
                Integer.class, auction) == 1);

        doThrow(new IllegalStateException("auction store is down")).when(auctions).completeSale(any());

        mockMvc.perform(post("/api/payments/" + paymentId + "/pay").header("Authorization", "Bearer " + winner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isInternalServerError());

        // EN: Nothing half-done: the payment is still open, the order still waiting, the lot still ENDED.
        // VI: Không có gì dở dang: thanh toán vẫn mở, đơn vẫn chờ, lô vẫn ENDED.
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE id = ?::uuid", String.class, paymentId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE payment_id = ?::uuid", String.class, paymentId))
                .isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, auction))
                .isEqualTo("ENDED");

        doCallRealMethod().when(auctions).completeSale(any());

        mockMvc.perform(post("/api/payments/" + paymentId + "/pay").header("Authorization", "Bearer " + winner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE payment_id = ?::uuid", String.class, paymentId))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, auction))
                .isEqualTo("COMPLETED");
    }
}
