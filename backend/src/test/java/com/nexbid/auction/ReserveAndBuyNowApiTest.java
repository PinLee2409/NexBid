package com.nexbid.auction;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: A hidden reserve and a buy-now price. Bidders only learn whether the reserve is met; a lot that closes below it
 *     waits 24 hours for the seller; buy-now sells at once, until the first bid.
 * VI: Giá sàn ẩn và giá mua ngay. Người trả giá chỉ biết giá sàn đã đạt chưa; lô đóng dưới giá sàn chờ người bán 24 giờ;
 *     mua ngay bán tức thì, cho tới lượt trả giá đầu tiên.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class ReserveAndBuyNowApiTest {

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

    private record Lot(String id, String productId) {
    }

    private Account account(String tag, RoleName role) throws Exception {
        String email = "reserve." + tag + "." + UUID.randomUUID() + "@nexbid.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Reserve %s","email":"%s","password":"supersecret"}
                """.formatted(tag, email))).andExpect(status().isCreated());
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"supersecret"}
                        """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return new Account(users.findByEmail(email).orElseThrow().id(),
                json.readTree(body).get("data").get("accessToken").asString());
    }

    private ResultActions as(Account who, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + who.token()));
    }

    private String product(Account seller, String name) throws Exception {
        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String body = as(seller, post("/api/seller/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","description":"A long description of the item.",
                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                """.formatted(name, json.readTree(categories).get("data").get(0).get("id").asString())))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("id").asString();
    }

    /** EN: Starting at 10m in steps of 0.5m. / VI: Khởi điểm 10 triệu, bước giá 0,5 triệu. */
    private ResultActions create(Account seller, String productId, String reserve, String buyNow) throws Exception {
        return as(seller, post("/api/seller/auctions").contentType(MediaType.APPLICATION_JSON).content("""
                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,"startTime":"%s","endTime":"%s",
                 "reservePrice":%s,"buyNowPrice":%s}
                """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)), Instant.now().plus(Duration.ofHours(4)),
                reserve, buyNow)));
    }

    private Lot openLot(Account seller, Account admin, String name, String reserve, String buyNow) throws Exception {
        String productId = product(seller, name);
        String body = create(seller, productId, reserve, buyNow).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("data").get("id").asString();
        as(seller, post("/api/seller/auctions/" + id + "/submit"));
        as(admin, post("/api/admin/auctions/" + id + "/approve"));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid", id);
        return new Lot(id, productId);
    }

    private ResultActions bid(Account bidder, Lot lot, String amount) throws Exception {
        return as(bidder, post("/api/auctions/" + lot.id() + "/bids").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    private void close(Lot lot) {
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", lot.id());
        auctions.endDueAuctions(Instant.now(), 200);
    }

    private ResultActions decide(Account seller, Lot lot, boolean accept) throws Exception {
        return as(seller, post("/api/seller/auctions/" + lot.id() + "/reserve-decision")
                .contentType(MediaType.APPLICATION_JSON).content("{\"accept\":" + accept + "}"));
    }

    private String column(String table, String column, String id) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM " + table + " WHERE id = ?::uuid", String.class, id);
    }

    private int payments(Lot lot) {
        return jdbc.queryForObject("SELECT count(*) FROM payments WHERE auction_id = ?::uuid", Integer.class, lot.id());
    }

    private int notices(UUID user, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?",
                Integer.class, user, type);
    }

    private String standingOf(Account bidder, Lot lot) throws Exception {
        String body = as(bidder, get("/api/users/me/bids")).andReturn().getResponse().getContentAsString();
        for (var row : json.readTree(body).get("data")) {
            if (row.get("auction").get("auction").get("id").asString().equals(lot.id())) {
                return row.get("standing").asString();
            }
        }
        return null;
    }

    @Test
    void thePricesMustHangTogetherAndTheReserveStaysHidden() throws Exception {
        Account seller = account("prices.seller", RoleName.SELLER);
        Account admin = account("prices.admin", RoleName.ADMIN);
        String productId = product(seller, "Priced lot");

        for (String[] prices : new String[][] { { "9000000", "null" }, { "null", "10000000" }, { "15000000", "14000000" } }) {
            create(seller, productId, prices[0], prices[1]).andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.code").value("AUCTION_PRICES_INVALID"));
        }

        Lot lot = openLot(seller, admin, "Hidden reserve", "12000000", "20000000");
        as(seller, get("/api/seller/auctions/" + lot.id()))
                .andExpect(jsonPath("$.data.reservePrice").value(12000000))
                .andExpect(jsonPath("$.data.buyNowPrice").value(20000000));
        mockMvc.perform(get("/api/auctions/" + lot.id()))
                .andExpect(jsonPath("$.data.auction.reservePrice").doesNotExist())
                .andExpect(jsonPath("$.data.auction.hasReserve").value(true))
                .andExpect(jsonPath("$.data.auction.reserveMet").value(false))
                .andExpect(jsonPath("$.data.auction.buyNowPrice").value(20000000));

        Account bidder = account("prices.bidder", RoleName.BUYER);
        bid(bidder, lot, "12000000").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reserveMet").value(true));
        mockMvc.perform(get("/api/auctions/" + lot.id())).andExpect(jsonPath("$.data.auction.reserveMet").value(true));
    }

    @Test
    void aLotClosingBelowItsReserveWaitsForTheSellerWhoMaySellAnyway() throws Exception {
        Account seller = account("below.seller", RoleName.SELLER);
        Account admin = account("below.admin", RoleName.ADMIN);
        Account low = account("below.low", RoleName.BUYER);
        Account high = account("below.high", RoleName.BUYER);
        Lot lot = openLot(seller, admin, "Below reserve", "12000000", "null");
        bid(low, lot, "10000000").andExpect(status().isCreated()).andExpect(jsonPath("$.data.reserveMet").value(false));
        bid(high, lot, "10500000").andExpect(status().isCreated());

        close(lot);

        // EN: No winner and no payment yet; the product stays held. / VI: Chưa có người thắng, chưa có thanh toán; sản phẩm vẫn được giữ.
        assertThat(column("auctions", "status", lot.id())).isEqualTo("ENDED");
        assertThat(column("auctions", "winner_id", lot.id())).isNull();
        assertThat(column("auctions", "reserve_decision", lot.id())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT reserve_decision_until BETWEEN now() + interval '23 hours' AND now() + "
                + "interval '25 hours' FROM auctions WHERE id = ?::uuid", Boolean.class, lot.id())).isTrue();
        assertThat(column("products", "status", lot.productId())).isEqualTo("IN_AUCTION");
        assertThat(payments(lot)).isZero();
        assertThat(standingOf(high, lot)).isEqualTo("AWAITING_SELLER");
        assertThat(standingOf(low, lot)).isEqualTo("LOST");
        await().atMost(Duration.ofSeconds(10)).until(() -> notices(high.id(), "RESERVE_NOT_MET") == 1
                && notices(seller.id(), "RESERVE_NOT_MET") == 1 && notices(low.id(), "AUCTION_LOST") == 1);

        // EN: Only the seller decides. / VI: Chỉ người bán quyết định.
        Account stranger = account("below.stranger", RoleName.SELLER);
        decide(stranger, lot, true).andExpect(status().isNotFound());

        decide(seller, lot, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reserveDecision").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.winnerId").value(high.id().toString()));
        decide(seller, lot, false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVE_DECISION_CLOSED"));

        // EN: From here it is an ordinary sale at the highest bid. / VI: Từ đây là giao dịch bình thường ở giá cao nhất.
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE auction_id = ?::uuid AND user_id = ?",
                BigDecimal.class, lot.id(), high.id())).isEqualByComparingTo("10500000");
        assertThat(jdbc.queryForObject("SELECT expired_at > now() + interval '47 hours' FROM payments "
                + "WHERE auction_id = ?::uuid", Boolean.class, lot.id())).isTrue();
        assertThat(standingOf(high, lot)).isEqualTo("WON");
        await().atMost(Duration.ofSeconds(10)).until(() -> notices(high.id(), "AUCTION_WON") == 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?::uuid AND action = "
                + "'RESERVE_ACCEPTED'", Integer.class, lot.id())).isEqualTo(1);
    }

    @Test
    void aRefusedOrLapsedDecisionLeavesTheLotUnsoldAndFreesTheProduct() throws Exception {
        Account seller = account("refuse.seller", RoleName.SELLER);
        Account admin = account("refuse.admin", RoleName.ADMIN);
        Account bidder = account("refuse.bidder", RoleName.BUYER);
        Lot refused = openLot(seller, admin, "Refused lot", "15000000", "null");
        Lot lapsed = openLot(seller, admin, "Lapsed lot", "15000000", "null");
        bid(bidder, refused, "10000000").andExpect(status().isCreated());
        bid(bidder, lapsed, "10000000").andExpect(status().isCreated());
        close(refused);
        close(lapsed);

        decide(seller, refused, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reserveDecision").value("DECLINED"));
        assertThat(column("products", "status", refused.productId())).isEqualTo("AVAILABLE");
        assertThat(payments(refused)).isZero();
        assertThat(standingOf(bidder, refused)).isEqualTo("LOST");

        // EN: The clock decides for a seller who never answers. / VI: Đồng hồ quyết định thay người bán không trả lời.
        jdbc.update("UPDATE auctions SET reserve_decision_until = now() - interval '1 second' WHERE id = ?::uuid",
                lapsed.id());
        decide(seller, lapsed, true).andExpect(status().isConflict());
        assertThat(auctions.lapseReserveDecisions(Instant.now(), 500)).isGreaterThanOrEqualTo(1);
        assertThat(column("auctions", "reserve_decision", lapsed.id())).isEqualTo("LAPSED");
        assertThat(column("products", "status", lapsed.productId())).isEqualTo("AVAILABLE");

        // EN: Either way the seller can list it again. / VI: Đằng nào người bán cũng đăng lại được.
        create(seller, refused.productId(), "null", "null").andExpect(status().isCreated());
        await().atMost(Duration.ofSeconds(10)).until(() -> notices(bidder.id(), "AUCTION_LOST") == 2
                && jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND title = 'Lot not sold'",
                        Integer.class, seller.id()) == 1);
    }

    @Test
    void aLotThatReachesItsReserveSellsAsUsual() throws Exception {
        Account seller = account("met.seller", RoleName.SELLER);
        Account admin = account("met.admin", RoleName.ADMIN);
        Account bidder = account("met.bidder", RoleName.BUYER);
        Lot lot = openLot(seller, admin, "Met reserve", "11000000", "null");
        bid(bidder, lot, "11000000").andExpect(status().isCreated());

        close(lot);

        assertThat(column("auctions", "winner_id", lot.id())).isEqualTo(bidder.id().toString());
        assertThat(column("auctions", "reserve_decision", lot.id())).isNull();
        assertThat(payments(lot)).isEqualTo(1);
    }

    @Test
    void anAutoBidThatCoversTheReserveDoesNotStopBelowIt() throws Exception {
        Account seller = account("proxy.seller", RoleName.SELLER);
        Account admin = account("proxy.admin", RoleName.ADMIN);
        Account bidder = account("proxy.bidder", RoleName.BUYER);
        Lot lot = openLot(seller, admin, "Proxy reserve", "12000000", "null");

        as(bidder, post("/api/auctions/" + lot.id() + "/auto-bid").contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxAmount\":15000000}")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/auctions/" + lot.id()))
                .andExpect(jsonPath("$.data.auction.currentPrice").value(12000000))
                .andExpect(jsonPath("$.data.auction.reserveMet").value(true));
        // EN: A ceiling below the reserve is not lifted past it. / VI: Mức trần thấp hơn giá sàn thì không bị đẩy vượt lên.
        Lot higher = openLot(seller, admin, "Proxy short", "20000000", "null");
        as(bidder, post("/api/auctions/" + higher.id() + "/auto-bid").contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxAmount\":15000000}")).andExpect(status().isCreated());
        mockMvc.perform(get("/api/auctions/" + higher.id()))
                .andExpect(jsonPath("$.data.auction.currentPrice").value(10000000))
                .andExpect(jsonPath("$.data.auction.reserveMet").value(false));
    }

    @Test
    void buyNowEndsTheLotAtOnceButOnlyUntilTheFirstBid() throws Exception {
        Account seller = account("now.seller", RoleName.SELLER);
        Account admin = account("now.admin", RoleName.ADMIN);
        Account buyer = account("now.buyer", RoleName.BUYER);
        Account late = account("now.late", RoleName.BUYER);
        Lot lot = openLot(seller, admin, "Buy it now", "12000000", "20000000");

        mockMvc.perform(post("/api/auctions/" + lot.id() + "/buy-now")).andExpect(status().isUnauthorized());
        as(seller, post("/api/auctions/" + lot.id() + "/buy-now")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_CANNOT_BID"));

        as(buyer, post("/api/auctions/" + lot.id() + "/buy-now")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPrice").value(20000000))
                .andExpect(jsonPath("$.data.leading").value(true));

        assertThat(column("auctions", "status", lot.id())).isEqualTo("ENDED");
        assertThat(column("auctions", "winner_id", lot.id())).isEqualTo(buyer.id().toString());
        assertThat(column("auctions", "bought_now", lot.id())).isEqualTo("true");
        assertThat(jdbc.queryForObject("SELECT end_time <= now() FROM auctions WHERE id = ?::uuid", Boolean.class, lot.id()))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT amount FROM bids WHERE auction_id = ?::uuid", BigDecimal.class, lot.id()))
                .isEqualByComparingTo("20000000");
        assertThat(jdbc.queryForObject("SELECT amount FROM payments WHERE auction_id = ?::uuid", BigDecimal.class, lot.id()))
                .isEqualByComparingTo("20000000");
        as(late, post("/api/auctions/" + lot.id() + "/buy-now")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_ALREADY_ENDED"));
        await().atMost(Duration.ofSeconds(10)).until(() -> notices(buyer.id(), "AUCTION_WON") == 1);

        // EN: Once someone bids, the offer is gone; a lot without one never had it. / VI: Có người trả giá là hết mua ngay; lô không có giá mua ngay thì chưa bao giờ có.
        Lot bidOn = openLot(seller, admin, "Bid first", "null", "20000000");
        bid(late, bidOn, "10000000").andExpect(status().isCreated());
        as(buyer, post("/api/auctions/" + bidOn.id() + "/buy-now")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUY_NOW_UNAVAILABLE"));
        Lot plain = openLot(seller, admin, "No buy now", "null", "null");
        as(buyer, post("/api/auctions/" + plain.id() + "/buy-now")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUY_NOW_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?::uuid AND action = 'BOUGHT_NOW'",
                Integer.class, lot.id())).isEqualTo(1);
    }
}
