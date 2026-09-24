package com.nexbid.auction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Creating an auction (guide §14, spec §7.4). Every validation the guide lists has a test here.
 * VI: Tạo phiên đấu giá (guide §14, spec §7.4). Mỗi luật kiểm tra guide liệt kê đều có một test ở đây.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class CreateAuctionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Auction Test","email":"%s","password":"supersecret"}
                        """.formatted(email)));

        users.grantRole(email, RoleName.SELLER);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    private String availableProduct(String token) throws Exception {
        String categories = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();

        String body = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Lot item","description":"...","categoryId":"%s",
                                 "condition":"GOOD","publishNow":true}
                                """.formatted(categoryId)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    private static String body(String productId, String price, String increment, Instant start, Instant end) {
        return """
                {"productId":"%s","startingPrice":%s,"minimumIncrement":%s,
                 "startTime":"%s","endTime":"%s","antiSnipingEnabled":true}
                """.formatted(productId, price, increment, start, end);
    }

    private static Instant inHours(long hours) {
        return Instant.now().plus(Duration.ofHours(hours));
    }

    @Test
    void aSellerCreatesADraftAuction() throws Exception {
        String token = tokenFor("auc.create@nexbid.com");
        String product = availableProduct(token);

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "10000000", "500000", inHours(1), inHours(2))))
                .andExpect(status().isCreated())
                // EN: Guide §14 says the initial status is DRAFT.
                // VI: Guide §14 nói trạng thái ban đầu là DRAFT.
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.productId").value(product))
                // EN: Nobody has bid, so the current price opens at the starting price.
                // VI: Chưa ai trả giá, nên giá hiện tại mở đúng bằng giá khởi điểm.
                .andExpect(jsonPath("$.data.currentPrice").value(10000000))
                .andExpect(jsonPath("$.data.bidCount").value(0))
                .andExpect(jsonPath("$.data.antiSniping.enabled").value(true))
                .andExpect(jsonPath("$.data.antiSniping.windowSeconds").value(30))
                .andExpect(jsonPath("$.data.antiSniping.extensionSeconds").value(120));
    }

    @Test
    void endTimeMustBeAfterStartTime() throws Exception {
        String token = tokenFor("auc.backwards@nexbid.com");
        String product = availableProduct(token);

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "1000", "10", inHours(3), inHours(2))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUCTION_SCHEDULE_INVALID"));
    }

    @Test
    void anAuctionCannotEndInThePast() throws Exception {
        String token = tokenFor("auc.past@nexbid.com");
        String product = availableProduct(token);

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "1000", "10", inHours(-4), inHours(-2))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUCTION_SCHEDULE_INVALID"));
    }

    @Test
    void pricesMustBeGreaterThanZero() throws Exception {
        String token = tokenFor("auc.zero@nexbid.com");
        String product = availableProduct(token);

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "0", "0", inHours(1), inHours(2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.startingPrice").exists())
                .andExpect(jsonPath("$.details.minimumIncrement").exists());
    }

    @Test
    void aSellerCannotAuctionAnotherSellersProduct() throws Exception {
        String owner = tokenFor("auc.owner@nexbid.com");
        String stranger = tokenFor("auc.stranger@nexbid.com");
        String product = availableProduct(owner);

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "1000", "10", inHours(1), inHours(2))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void aProductCannotHaveTwoLiveAuctions() throws Exception {
        String token = tokenFor("auc.twice@nexbid.com");
        String product = availableProduct(token);

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "1000", "10", inHours(1), inHours(2))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "2000", "20", inHours(3), inHours(4))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_ALREADY_IN_AUCTION"));
    }

    @Test
    void aDraftProductCannotGoToAuction() throws Exception {
        String token = tokenFor("auc.draftproduct@nexbid.com");
        String product = availableProduct(token);

        jdbc.update("UPDATE products SET status = 'DRAFT' WHERE id = ?::uuid", product);

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "1000", "10", inHours(1), inHours(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_SELLABLE"));
    }

    @Test
    void anUnknownProductIs404() throws Exception {
        String token = tokenFor("auc.noproduct@nexbid.com");

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "1000", "10", inHours(1), inHours(2))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void aDraftCanBeEdited() throws Exception {
        String token = tokenFor("auc.edit@nexbid.com");
        String product = availableProduct(token);

        String created = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "1000", "10", inHours(1), inHours(2))))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("data").get("id").asString();

        mockMvc.perform(put("/api/seller/auctions/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "5000", "250", inHours(2), inHours(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startingPrice").value(5000))
                // EN: No bids yet, so raising the opening price moves the current price with it.
                // VI: Chưa có lượt trả giá nào, nên nâng giá khởi điểm thì giá hiện tại đi theo.
                .andExpect(jsonPath("$.data.currentPrice").value(5000));
    }

    @Test
    void anAuctionSentForApprovalIsNoLongerTheSellersToChange() throws Exception {
        String token = tokenFor("auc.locked@nexbid.com");
        String product = availableProduct(token);

        String created = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "1000", "10", inHours(1), inHours(2))))
                .andReturn().getResponse().getContentAsString();

        String id = objectMapper.readTree(created).get("data").get("id").asString();

        // EN: Submission arrives at function 13; the status is set directly to prove the rule now.
        // VI: Việc gửi duyệt tới ở chức năng 13; tạm đặt thẳng trạng thái để chứng minh luật này ngay.
        jdbc.update("UPDATE auctions SET status = 'PENDING_APPROVAL' WHERE id = ?::uuid", id);

        // EN: Spec §7.4 — after it is sent, the terms an admin sees must be the terms that run.
        // VI: Spec §7.4 — đã gửi rồi thì điều khoản admin nhìn thấy phải là điều khoản thực sự chạy.
        mockMvc.perform(put("/api/seller/auctions/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(product, "1", "1", inHours(1), inHours(2))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_EDITABLE"));
    }

    @Test
    void oneSellerNeverSeesAnothersAuctions() throws Exception {
        String alice = tokenFor("auc.alice@nexbid.com");
        String bob = tokenFor("auc.bob@nexbid.com");

        String bobsProduct = availableProduct(bob);
        String created = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bobsProduct, "1000", "10", inHours(1), inHours(2))))
                .andReturn().getResponse().getContentAsString();

        String bobsAuction = objectMapper.readTree(created).get("data").get("id").asString();

        mockMvc.perform(get("/api/seller/auctions/" + bobsAuction)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }

    @Test
    void aBuyerCannotCreateAuctions() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Plain","email":"auc.buyer@nexbid.com","password":"supersecret"}
                        """));

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"auc.buyer@nexbid.com","password":"supersecret"}
                                """))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(body).get("data").get("accessToken").asString();

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(UUID.randomUUID().toString(), "1000", "10", inHours(1), inHours(2))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
