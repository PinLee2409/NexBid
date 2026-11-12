package com.nexbid.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
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
import com.nexbid.support.TestInfrastructure;
import com.nexbid.support.VnpayTestSupport;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Buyers rate sellers: once per order, only once the goods arrived, and anyone may read a seller's score.
 * VI: Người mua đánh giá người bán: mỗi đơn một lần, chỉ sau khi đã nhận hàng, và ai cũng đọc được điểm của người bán.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class ReviewApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionService auctions;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private record Account(UUID id, String token) {
    }

    private Account account(String name, RoleName role) throws Exception {
        String email = "review." + UUID.randomUUID() + "@nexbid.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"%s","email":"%s","password":"supersecret"}
                """.formatted(name, email))).andExpect(status().isCreated());
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"supersecret"}
                        """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return new Account(users.findByEmail(email).orElseThrow().id(),
                json.readTree(body).get("data").get("accessToken").asString());
    }

    private ResultActions as(Account who, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + who.token()));
    }

    /** EN: A lot the buyer won and paid for; returns the order id. / VI: Một lô người mua đã thắng và trả tiền; trả về id đơn. */
    private String paidOrder(Account seller, Account admin, Account buyer, String name) throws Exception {
        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = json.readTree(categories).get("data").get(0).get("id").asString();
        String product = as(seller, post("/api/seller/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","description":"A long description of the item.",
                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                """.formatted(name, categoryId))).andReturn().getResponse().getContentAsString();
        String auction = as(seller, post("/api/seller/auctions").contentType(MediaType.APPLICATION_JSON).content("""
                {"productId":"%s","startingPrice":1000000,"minimumIncrement":100000,"startTime":"%s","endTime":"%s"}
                """.formatted(json.readTree(product).get("data").get("id").asString(),
                Instant.now().plus(Duration.ofMinutes(30)), Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(auction).get("data").get("id").asString();
        as(seller, post("/api/seller/auctions/" + id + "/submit"));
        as(admin, post("/api/admin/auctions/" + id + "/approve"));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid", id);
        as(buyer, post("/api/auctions/" + id + "/bids").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000000}"))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", id);
        auctions.endDueAuctions(Instant.now(), 200);

        String payment = jdbc.queryForObject("SELECT id::text FROM payments WHERE auction_id = ?::uuid", String.class, id);
        VnpayTestSupport.pay(mockMvc, buyer.token(), payment, "SUCCESS").andExpect(status().isOk());
        return jdbc.queryForObject("SELECT id::text FROM orders WHERE payment_id = ?::uuid", String.class, payment);
    }

    private String completedOrder(Account seller, Account admin, Account buyer, String name) throws Exception {
        String order = paidOrder(seller, admin, buyer, name);
        as(seller, post("/api/seller/orders/" + order + "/ship")).andExpect(status().isOk());
        as(buyer, post("/api/users/me/orders/" + order + "/received")).andExpect(status().isOk());
        return order;
    }

    private ResultActions review(Account buyer, String order, String body) throws Exception {
        return as(buyer, post("/api/users/me/orders/" + order + "/review").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void aBuyerRatesTheSellerOnceTheGoodsHaveArrived() throws Exception {
        Account seller = account("Rating Seller", RoleName.SELLER);
        Account admin = account("Rating Admin", RoleName.ADMIN);
        Account buyer = account("Quinn Buyer", RoleName.BUYER);
        String order = paidOrder(seller, admin, buyer, "Reviewed camera");

        // EN: Paid is not enough, nor shipped: only received. / VI: Đã trả tiền chưa đủ, đã gửi cũng chưa: phải đã nhận.
        review(buyer, order, "{\"rating\":4}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_ALLOWED"));
        as(seller, post("/api/seller/orders/" + order + "/ship")).andExpect(status().isOk());
        review(buyer, order, "{\"rating\":4}").andExpect(status().isConflict());
        as(buyer, post("/api/users/me/orders/" + order + "/received")).andExpect(status().isOk());

        Account stranger = account("Stranger", RoleName.BUYER);
        review(stranger, order, "{\"rating\":1}").andExpect(status().isNotFound());

        review(buyer, order, "{\"rating\":4,\"comment\":\"  Well packed, as described.  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.comment").value("Well packed, as described."))
                .andExpect(jsonPath("$.data.reviewer").value("qui***"));
        review(buyer, order, "{\"rating\":5}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVIEW_EXISTS"));

        // EN: Anyone may read the score, signed in or not. / VI: Ai cũng đọc được điểm, đăng nhập hay không.
        mockMvc.perform(get("/api/sellers/" + seller.id() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.average").value(4.0))
                .andExpect(jsonPath("$.data.reviewCount").value(1))
                .andExpect(jsonPath("$.data.sales").value(1))
                .andExpect(jsonPath("$.data.reviews.items[0].lotName").value("Reviewed camera"))
                .andExpect(jsonPath("$.data.reviews.items[0].reviewer").value("qui***"));
        as(buyer, get("/api/users/me/orders"))
                .andExpect(jsonPath("$.data[0].review.rating").value(4));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'NEW_REVIEW'",
                Integer.class, seller.id())).isEqualTo(1);
    }

    @Test
    void theScoreAveragesEveryReviewAndSalesCountEveryPaidOrder() throws Exception {
        Account seller = account("Average Seller", RoleName.SELLER);
        Account admin = account("Average Admin", RoleName.ADMIN);
        Account first = account("First Buyer", RoleName.BUYER);
        Account second = account("Second Buyer", RoleName.BUYER);

        review(first, completedOrder(seller, admin, first, "Lot one"), "{\"rating\":4,\"comment\":\"   \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.comment").doesNotExist());
        review(second, completedOrder(seller, admin, second, "Lot two"), "{\"rating\":5}").andExpect(status().isCreated());
        // EN: Paid but not yet received: a sale, not yet a review. / VI: Đã trả nhưng chưa nhận: là lượt bán, chưa có đánh giá.
        paidOrder(seller, admin, first, "Lot three");

        mockMvc.perform(get("/api/sellers/" + seller.id() + "/reviews").param("size", "1"))
                .andExpect(jsonPath("$.data.average").value(4.5))
                .andExpect(jsonPath("$.data.reviewCount").value(2))
                .andExpect(jsonPath("$.data.sales").value(3))
                .andExpect(jsonPath("$.data.reviews.items.length()").value(1))
                .andExpect(jsonPath("$.data.reviews.items[0].lotName").value("Lot two"));
    }

    @Test
    void aReviewNeedsOneToFiveStarsAndAShortComment() throws Exception {
        Account seller = account("Rules Seller", RoleName.SELLER);
        Account admin = account("Rules Admin", RoleName.ADMIN);
        Account buyer = account("Rules Buyer", RoleName.BUYER);
        String order = completedOrder(seller, admin, buyer, "Rules lot");

        for (String body : new String[] { "{}", "{\"rating\":0}", "{\"rating\":6}",
                "{\"rating\":3,\"comment\":\"" + "x".repeat(501) + "\"}" }) {
            review(buyer, order, body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mockMvc.perform(post("/api/users/me/orders/" + order + "/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":3}")).andExpect(status().isUnauthorized());
    }

    @Test
    void aSellerWithNoReviewsHasNoScoreYet() throws Exception {
        mockMvc.perform(get("/api/sellers/" + UUID.randomUUID() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.average").doesNotExist())
                .andExpect(jsonPath("$.data.reviewCount").value(0))
                .andExpect(jsonPath("$.data.sales").value(0));
    }
}
