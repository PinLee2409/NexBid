package com.nexbid.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.nexbid.auction.AuctionService;
import com.nexbid.payment.PaymentService;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Orders (guide §33): opened at the win, PAID when the payment succeeds — which completes the lot and
 *     sells the product — and CANCELLED when the payment window closes unpaid.
 * VI: Đơn hàng (guide §33): mở lúc thắng, PAID khi thanh toán thành công — kéo theo lô hoàn tất và sản phẩm
 *     được bán — và CANCELLED khi quá hạn thanh toán mà chưa trả.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class OrderApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionService auctions;

    @Autowired
    private PaymentService payments;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private record Won(String auctionId, String productId, String paymentId, String winner, String seller) {
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

    private UUID idOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("data").get("id").asString());
    }

    private String[] openLot(String seller, String admin, String name) throws Exception {
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
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();
        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auctionId);
        return new String[] { auctionId, productId };
    }

    private void close(String auctionId) {
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", auctionId);
        auctions.endDueAuctions(Instant.now(), 200);
    }

    private Won wonLot(String tag) throws Exception {
        String seller = tokenFor("ord.seller." + tag + "@nexbid.com", "Seller " + tag, RoleName.SELLER);
        String admin = tokenFor("ord.admin." + tag + "@nexbid.com", "Admin " + tag, RoleName.ADMIN);
        String winner = tokenFor("ord.winner." + tag + "@nexbid.com", "Winner " + tag, RoleName.BUYER);
        String[] lot = openLot(seller, admin, "Lot " + tag);

        mockMvc.perform(post("/api/auctions/" + lot[0] + "/bids").header("Authorization", "Bearer " + winner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":12000000}"))
                .andExpect(status().isCreated());
        close(lot[0]);

        String paymentId = jdbc.queryForObject(
                "SELECT id::text FROM payments WHERE auction_id = ?::uuid", String.class, lot[0]);
        return new Won(lot[0], lot[1], paymentId, winner, seller);
    }

    private ResultActions pay(Won won, String outcome) throws Exception {
        return mockMvc.perform(post("/api/payments/" + won.paymentId() + "/pay")
                .header("Authorization", "Bearer " + won.winner())
                .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"" + outcome + "\"}"));
    }

    private String statusIn(String table, String id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?::uuid", String.class, id);
    }

    private String orderOf(Won won) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE auction_id = ?::uuid", String.class, won.auctionId());
    }

    @Test
    void theWinOpensAnOrderWaitingOnPayment() throws Exception {
        Won won = wonLot("open");

        var row = jdbc.queryForMap("SELECT * FROM orders WHERE auction_id = ?::uuid", won.auctionId());
        assertThat(row.get("status")).isEqualTo("PENDING_PAYMENT");
        assertThat(row.get("buyer_id")).isEqualTo(idOf(won.winner()));
        assertThat(row.get("seller_id")).isEqualTo(idOf(won.seller()));
        assertThat(row.get("payment_id").toString()).isEqualTo(won.paymentId());
        assertThat((java.math.BigDecimal) row.get("amount")).isEqualByComparingTo("12000000");

        // EN: The shape the /orders page already renders: order, payment, lot.
        // VI: Đúng hình dạng mà trang /orders đang hiển thị: đơn, thanh toán, lô.
        mockMvc.perform(get("/api/users/me/orders").header("Authorization", "Bearer " + won.winner()))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].order.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data[0].order.paymentId").value(won.paymentId()))
                .andExpect(jsonPath("$.data[0].payment.status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].auction.product.name").value("Lot open"));
    }

    @Test
    void payingCompletesTheSaleEverywhereAtOnce() throws Exception {
        Won won = wonLot("paid");

        pay(won, "SUCCESS").andExpect(status().isOk());

        // EN: Spec §17: SUCCESS → Order PAID → Auction COMPLETED, and the item is sold.
        // VI: Spec §17: SUCCESS → Đơn PAID → Phiên COMPLETED, và món hàng đã được bán.
        assertThat(orderOf(won)).isEqualTo("PAID");
        assertThat(statusIn("auctions", won.auctionId())).isEqualTo("COMPLETED");
        assertThat(statusIn("products", won.productId())).isEqualTo("SOLD");

        String orderId = jdbc.queryForObject("SELECT id::text FROM orders WHERE auction_id = ?::uuid",
                String.class, won.auctionId());
        mockMvc.perform(get("/api/users/me/orders/" + orderId).header("Authorization", "Bearer " + won.winner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("PAID"))
                .andExpect(jsonPath("$.data.payment.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.auction.auction.status").value("COMPLETED"));

        // EN: A completed lot stays public and stays on the winner's list. / VI: Lô hoàn tất vẫn công khai và vẫn nằm trong danh sách thắng.
        mockMvc.perform(get("/api/auctions/" + won.auctionId())).andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me/wins").header("Authorization", "Bearer " + won.winner()))
                .andExpect(jsonPath("$.data[0].auction.status").value("COMPLETED"));

        // EN: A sold product cannot be sold again. / VI: Sản phẩm đã bán thì không bán lại được.
        mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + won.seller())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":9000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(won.productId(), Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_SELLABLE"));
    }

    @Test
    void aFailedAttemptLeavesTheOrderWaiting() throws Exception {
        Won won = wonLot("failed");

        pay(won, "FAILED").andExpect(status().isOk());

        assertThat(orderOf(won)).isEqualTo("PENDING_PAYMENT");
        assertThat(statusIn("auctions", won.auctionId())).isEqualTo("ENDED");
    }

    @Test
    void anExpiredPaymentCancelsTheOrder() throws Exception {
        Won won = wonLot("expired");
        jdbc.update("UPDATE payments SET expired_at = now() - interval '1 second' WHERE id = ?::uuid", won.paymentId());

        payments.expireOverdue(Instant.now(), 500);

        assertThat(orderOf(won)).isEqualTo("CANCELLED");
        assertThat(statusIn("auctions", won.auctionId())).isEqualTo("CANCELLED");
    }

    @Test
    void aLotThatSoldNothingHasNoOrder() throws Exception {
        String seller = tokenFor("ord.seller.none@nexbid.com", "Seller None", RoleName.SELLER);
        String admin = tokenFor("ord.admin.none@nexbid.com", "Admin None", RoleName.ADMIN);
        String[] lot = openLot(seller, admin, "Unsold");

        close(lot[0]);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE auction_id = ?::uuid",
                Integer.class, lot[0])).isZero();
    }

    @Test
    void onlyTheBuyerSeesTheirOrder() throws Exception {
        Won won = wonLot("private");
        String stranger = tokenFor("ord.stranger@nexbid.com", "Stranger", RoleName.BUYER);
        String orderId = jdbc.queryForObject("SELECT id::text FROM orders WHERE auction_id = ?::uuid",
                String.class, won.auctionId());

        // EN: The seller included — guide §33 gives buyers their orders; a seller view is not part of it.
        // VI: Kể cả người bán — guide §33 chỉ cho người mua xem đơn của mình; màn cho người bán không nằm trong đó.
        for (String token : List.of(stranger, won.seller())) {
            mockMvc.perform(get("/api/users/me/orders/" + orderId).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
            mockMvc.perform(get("/api/users/me/orders").header("Authorization", "Bearer " + token))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
        mockMvc.perform(get("/api/users/me/orders/" + UUID.randomUUID()).header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void theEndpointsNeedASignedInCaller() throws Exception {
        mockMvc.perform(get("/api/users/me/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me/orders/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
