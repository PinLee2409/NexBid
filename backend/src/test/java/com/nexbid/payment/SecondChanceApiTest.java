package com.nexbid.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Second-chance offers (spec §17): when the winner does not pay, the seller may offer the lot once to the
 *     runner-up at their own highest bid; 24 hours to accept, then the usual 48-hour payment.
 * VI: Đề nghị cơ hội thứ hai (spec §17): khi người thắng không trả, người bán được đề nghị lô một lần cho người thứ
 *     hai với giá cao nhất của chính họ; 24 giờ để nhận, rồi thanh toán 48 giờ như thường lệ.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class SecondChanceApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionService auctions;

    @Autowired
    private PaymentService payments;

    @Autowired
    private SecondChanceService offers;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    /** EN: A lot its winner left unpaid, now cancelled. / VI: Một lô bị người thắng bỏ không trả, giờ đã huỷ. */
    private record Unpaid(String auctionId, String productId, String seller, String winner, String runnerUp) {
    }

    private String tokenFor(String tag, RoleName role) throws Exception {
        String email = "second." + tag + "." + UUID.randomUUID() + "@nexbid.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Second %s","email":"%s","password":"supersecret"}
                """.formatted(tag, email))).andExpect(status().isCreated());
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"supersecret"}
                        """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("accessToken").asString();
    }

    private UUID idOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("data").get("id").asString());
    }

    private String data(ResultActions result, String field) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data").get(field).asString();
    }

    /** EN: Open for bidding. Returns (auctionId, productId). / VI: Đang mở trả giá. Trả về (auctionId, productId). */
    private String[] openLot(String seller, String admin, String name) throws Exception {
        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = json.readTree(categories).get("data").get(0).get("id").asString();
        String productId = data(mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","description":"A long description of the item.",
                         "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                        """.formatted(name, categoryId))), "id");
        String auctionId = relist(seller, productId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        auctionId = json.readTree(auctionId).get("data").get("id").asString();

        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auctionId);
        return new String[] { auctionId, productId };
    }

    private ResultActions relist(String seller, String productId) throws Exception {
        return mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                         "startTime":"%s","endTime":"%s"}
                        """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)),
                        Instant.now().plus(Duration.ofHours(4)))));
    }

    private void bid(String token, String auctionId, String amount) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    /** EN: Closes the lot and lets the open payment on it lapse. / VI: Đóng lô và để khoản thanh toán đang mở trên nó quá hạn. */
    private void closeUnpaid(String auctionId) {
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", auctionId);
        auctions.endDueAuctions(Instant.now(), 200);
        lapse(auctionId);
    }

    private void lapse(String auctionId) {
        jdbc.update("UPDATE payments SET expired_at = now() - interval '1 second' "
                + "WHERE auction_id = ?::uuid AND status IN ('PENDING', 'FAILED')", auctionId);
        payments.expireOverdue(Instant.now(), 500);
    }

    /** EN: A low bid, the runner-up at 10.5m and the winner at 11m; the winner never pays. / VI: Một giá thấp, người thứ hai ở 10,5 triệu và người thắng ở 11 triệu; người thắng không bao giờ trả. */
    private Unpaid unpaidLot(String tag) throws Exception {
        String seller = tokenFor(tag + ".seller", RoleName.SELLER);
        String admin = tokenFor(tag + ".admin", RoleName.ADMIN);
        String low = tokenFor(tag + ".low", RoleName.BUYER);
        String runnerUp = tokenFor(tag + ".runner", RoleName.BUYER);
        String winner = tokenFor(tag + ".winner", RoleName.BUYER);
        String[] lot = openLot(seller, admin, "Second " + tag);

        bid(low, lot[0], "10000000");
        bid(runnerUp, lot[0], "10500000");
        bid(winner, lot[0], "11000000");
        closeUnpaid(lot[0]);
        return new Unpaid(lot[0], lot[1], seller, winner, runnerUp);
    }

    private ResultActions offer(String seller, String auctionId) throws Exception {
        return mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/second-chance")
                .header("Authorization", "Bearer " + seller));
    }

    private ResultActions answer(String buyer, String offerId, String verb) throws Exception {
        return mockMvc.perform(post("/api/users/me/offers/" + offerId + "/" + verb)
                .header("Authorization", "Bearer " + buyer));
    }

    private String column(String table, String column, String id) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM " + table + " WHERE id = ?::uuid", String.class, id);
    }

    private int notices(UUID user, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?",
                Integer.class, user, type);
    }

    private int audits(String offerId, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?::uuid AND action = ?",
                Integer.class, offerId, action);
    }

    @Test
    void theRunnerUpBuysTheLotAtTheirOwnBid() throws Exception {
        Unpaid lot = unpaidLot("accept");

        mockMvc.perform(get("/api/seller/second-chances").header("Authorization", "Bearer " + lot.seller()))
                .andExpect(jsonPath("$.data[0].auctionId").value(lot.auctionId()))
                .andExpect(jsonPath("$.data[0].runnerUpBid").value(10500000))
                .andExpect(jsonPath("$.data[0].canOffer").value(true));

        String offerId = data(offer(lot.seller(), lot.auctionId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(10500000))
                .andExpect(jsonPath("$.data.status").value("PENDING")), "id");
        assertThat(jdbc.queryForObject("SELECT expires_at BETWEEN now() + interval '23 hours' AND now() + interval "
                + "'25 hours' FROM second_chance_offers WHERE id = ?::uuid", Boolean.class, offerId)).isTrue();

        // EN: Once per lot, and the product is held while the offer is open. / VI: Mỗi lô một lần, và sản phẩm được giữ khi đề nghị còn mở.
        offer(lot.seller(), lot.auctionId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_ALREADY_MADE"));
        assertThat(column("products", "status", lot.productId())).isEqualTo("IN_AUCTION");
        relist(lot.seller(), lot.productId()).andExpect(status().isConflict());

        // EN: Only the runner-up sees it and can answer it. / VI: Chỉ người thứ hai thấy và trả lời được.
        mockMvc.perform(get("/api/users/me/offers").header("Authorization", "Bearer " + lot.runnerUp()))
                .andExpect(jsonPath("$.data[0].offer.id").value(offerId))
                .andExpect(jsonPath("$.data[0].auction.product.name").value("Second accept"));
        mockMvc.perform(get("/api/users/me/offers").header("Authorization", "Bearer " + lot.winner()))
                .andExpect(jsonPath("$.data.length()").value(0));
        answer(lot.winner(), offerId, "accept").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_FOUND"));

        answer(lot.runnerUp(), offerId, "accept").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        answer(lot.runnerUp(), offerId, "decline").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_PENDING"));

        // EN: The lot is theirs now, with a fresh 48-hour payment at their price and the order behind it.
        // VI: Lô giờ là của họ, kèm khoản thanh toán 48 giờ mới theo giá của họ và đơn hàng đi cùng.
        UUID runnerUpId = idOf(lot.runnerUp());
        assertThat(column("auctions", "status", lot.auctionId())).isEqualTo("ENDED");
        assertThat(column("auctions", "winner_id", lot.auctionId())).isEqualTo(runnerUpId.toString());
        String paymentId = jdbc.queryForObject("SELECT id::text FROM payments WHERE auction_id = ?::uuid AND user_id = ?",
                String.class, lot.auctionId(), runnerUpId);
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE id = ?::uuid", BigDecimal.class, paymentId))
                .isEqualByComparingTo("10500000");
        assertThat(jdbc.queryForObject("SELECT expired_at > now() + interval '47 hours' FROM payments WHERE id = ?::uuid",
                Boolean.class, paymentId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE payment_id = ?::uuid", String.class, paymentId))
                .isEqualTo("PENDING_PAYMENT");
        mockMvc.perform(get("/api/users/me/wins").header("Authorization", "Bearer " + lot.runnerUp()))
                .andExpect(jsonPath("$.data[0].auction.id").value(lot.auctionId()));

        mockMvc.perform(post("/api/payments/" + paymentId + "/pay").header("Authorization", "Bearer " + lot.runnerUp())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isOk());
        assertThat(column("auctions", "status", lot.auctionId())).isEqualTo("COMPLETED");
        assertThat(column("products", "status", lot.productId())).isEqualTo("SOLD");

        UUID sellerId = idOf(lot.seller());
        await().atMost(Duration.ofSeconds(10)).until(() ->
                notices(runnerUpId, "SECOND_CHANCE_OFFER") == 1 && notices(sellerId, "SECOND_CHANCE_ACCEPTED") == 1);
        assertThat(audits(offerId, "OFFER_MADE")).isEqualTo(1);
        assertThat(audits(offerId, "OFFER_ACCEPTED")).isEqualTo(1);
    }

    @Test
    void aDeclinedOfferGivesTheProductBackForGood() throws Exception {
        Unpaid lot = unpaidLot("decline");
        String offerId = data(offer(lot.seller(), lot.auctionId()).andExpect(status().isCreated()), "id");

        answer(lot.runnerUp(), offerId, "decline").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DECLINED"));

        assertThat(column("auctions", "status", lot.auctionId())).isEqualTo("CANCELLED");
        assertThat(column("products", "status", lot.productId())).isEqualTo("AVAILABLE");
        offer(lot.seller(), lot.auctionId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_ALREADY_MADE"));
        relist(lot.seller(), lot.productId()).andExpect(status().isCreated());

        UUID sellerId = idOf(lot.seller());
        await().atMost(Duration.ofSeconds(10)).until(() -> notices(sellerId, "SECOND_CHANCE_DECLINED") == 1);
        assertThat(audits(offerId, "OFFER_DECLINED")).isEqualTo(1);
    }

    @Test
    void anOfferNobodyAnswersLapsesAfterItsDay() throws Exception {
        Unpaid lot = unpaidLot("lapse");
        String offerId = data(offer(lot.seller(), lot.auctionId()).andExpect(status().isCreated()), "id");
        jdbc.update("UPDATE second_chance_offers SET expires_at = now() - interval '1 second' WHERE id = ?::uuid",
                offerId);

        // EN: The clock decides, before the job has even run. / VI: Đồng hồ quyết định, kể cả khi job chưa chạy.
        answer(lot.runnerUp(), offerId, "accept").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_NOT_PENDING"));
        mockMvc.perform(get("/api/users/me/offers").header("Authorization", "Bearer " + lot.runnerUp()))
                .andExpect(jsonPath("$.data[0].offer.status").value("EXPIRED"));

        assertThat(offers.expireOverdue(Instant.now(), 500)).isGreaterThanOrEqualTo(1);
        assertThat(column("second_chance_offers", "status", offerId)).isEqualTo("EXPIRED");
        assertThat(column("products", "status", lot.productId())).isEqualTo("AVAILABLE");
        assertThat(column("auctions", "status", lot.auctionId())).isEqualTo("CANCELLED");

        UUID sellerId = idOf(lot.seller());
        await().atMost(Duration.ofSeconds(10)).until(() -> notices(sellerId, "SECOND_CHANCE_DECLINED") == 1);
        assertThat(jdbc.queryForObject("SELECT title FROM notifications WHERE user_id = ? AND type = 'SECOND_CHANCE_DECLINED'",
                String.class, sellerId)).isEqualTo("Offer lapsed");
        assertThat(audits(offerId, "OFFER_EXPIRED")).isEqualTo(1);
    }

    @Test
    void aRunnerUpWhoAcceptsButNeverPaysEndsTheSaleAgain() throws Exception {
        Unpaid lot = unpaidLot("twice");
        String offerId = data(offer(lot.seller(), lot.auctionId()).andExpect(status().isCreated()), "id");
        answer(lot.runnerUp(), offerId, "accept").andExpect(status().isOk());

        lapse(lot.auctionId());

        assertThat(column("auctions", "status", lot.auctionId())).isEqualTo("CANCELLED");
        assertThat(column("products", "status", lot.productId())).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE auction_id = ?::uuid AND status = 'EXPIRED'",
                Integer.class, lot.auctionId())).isEqualTo(2);
        offer(lot.seller(), lot.auctionId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OFFER_ALREADY_MADE"));

        // EN: The seller hears about each sale that fell through. / VI: Người bán được báo mỗi lần giao dịch đổ vỡ.
        UUID sellerId = idOf(lot.seller());
        await().atMost(Duration.ofSeconds(10)).until(() -> notices(sellerId, "AUCTION_CANCELLED") == 2);
    }

    @Test
    void onlyItsSellerMayOfferALotItsWinnerLeftUnpaidToSomeoneWhoBid() throws Exception {
        // EN: Nobody else bid. / VI: Không ai khác trả giá.
        String seller = tokenFor("rules.seller", RoleName.SELLER);
        String admin = tokenFor("rules.admin", RoleName.ADMIN);
        String only = tokenFor("rules.only", RoleName.BUYER);
        String[] alone = openLot(seller, admin, "Second alone");
        bid(only, alone[0], "10000000");
        closeUnpaid(alone[0]);

        mockMvc.perform(get("/api/seller/second-chances").header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.data[0].runnerUpBid").doesNotExist())
                .andExpect(jsonPath("$.data[0].canOffer").value(false));
        offer(seller, alone[0]).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_RUNNER_UP"));
        assertThat(column("products", "status", alone[1])).isEqualTo("AVAILABLE");

        // EN: Someone else's lot, and a lot whose winner may still pay. / VI: Lô của người khác, và lô mà người thắng vẫn còn trả được.
        Unpaid lot = unpaidLot("rules");
        offer(seller, lot.auctionId()).andExpect(status().isNotFound());
        String[] open = openLot(lot.seller(), admin, "Second still due");
        bid(only, open[0], "10000000");
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", open[0]);
        auctions.endDueAuctions(Instant.now(), 200);
        offer(lot.seller(), open[0]).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECOND_CHANCE_UNAVAILABLE"));

        // EN: Listed again before the seller thought of it. / VI: Đã đăng lại trước khi người bán nghĩ tới chuyện này.
        relist(lot.seller(), lot.productId()).andExpect(status().isCreated());
        offer(lot.seller(), lot.auctionId()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECOND_CHANCE_UNAVAILABLE"));
        mockMvc.perform(post("/api/seller/auctions/" + lot.auctionId() + "/second-chance")
                        .header("Authorization", "Bearer " + lot.runnerUp()))
                .andExpect(status().isForbidden());
    }
}
