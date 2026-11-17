package com.nexbid.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
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
import com.nexbid.support.Market;
import com.nexbid.support.Market.Account;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Offers on buy-now lots: the price bounds, a counter the buyer accepts (the lot sells and a payment opens), a
 *     declined offer, the per-lot limit, and offers closing once someone bids.
 * VI: Đề nghị trên lô có giá mua ngay: giới hạn giá, lời trả giá lại được người mua nhận (lô bán và mở khoản thanh
 *     toán), đề nghị bị từ chối, giới hạn mỗi lô, và đề nghị đóng khi có người trả giá.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class LotOfferApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService users;
    @Autowired
    private AuctionService auctions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private Market market;
    private Account seller;
    private Account admin;

    @BeforeEach
    void setUp() throws Exception {
        market = new Market(mockMvc, users, auctions, jdbc, json);
        seller = market.account("offer.seller", RoleName.SELLER);
        admin = market.account("offer.admin", RoleName.ADMIN);
    }

    private ResultActions offer(Account buyer, String lot, long amount) throws Exception {
        return market.as(buyer, post("/api/auctions/" + lot + "/offers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + ",\"message\":\"Would you take this?\"}"));
    }

    private String buyNowLot(String name) throws Exception {
        return market.openLot(seller, admin, name, 10_000_000, "\"buyNowPrice\":20000000");
    }

    @Test
    void aCounterTheBuyerAcceptsSellsTheLot() throws Exception {
        String lot = buyNowLot("Countered camera");
        Account buyer = market.account("offer.buyer", RoleName.BUYER);

        offer(buyer, lot, 9_000_000).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OFFER_INVALID"));
        offer(buyer, lot, 20_000_000).andExpect(status().isBadRequest());
        offer(seller, lot, 15_000_000).andExpect(jsonPath("$.code").value("SELLER_CANNOT_BID"));

        String offerId = market.data(offer(buyer, lot, 15_000_000).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))).get("id").asString();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'OFFER_RECEIVED'",
                Integer.class, seller.id())).isEqualTo(1);

        market.as(seller, get("/api/seller/lot-offers")).andExpect(jsonPath("$.data[0].id").value(offerId))
                .andExpect(jsonPath("$.data[0].buyer").value(org.hamcrest.Matchers.endsWith("***")));
        market.as(seller, post("/api/seller/lot-offers/" + offerId + "/counter").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":14000000}")).andExpect(jsonPath("$.code").value("OFFER_INVALID"));
        market.as(seller, post("/api/seller/lot-offers/" + offerId + "/counter").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":18000000}")).andExpect(jsonPath("$.data.status").value("COUNTERED"));
        // EN: The seller cannot accept their own counter; only the buyer can. / VI: Người bán không tự nhận giá trả lại của mình; chỉ người mua.
        market.as(seller, post("/api/seller/lot-offers/" + offerId + "/accept"))
                .andExpect(jsonPath("$.code").value("OFFER_UNAVAILABLE"));

        market.as(buyer, post("/api/users/me/lot-offers/" + offerId + "/accept-counter"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        assertThat(market.column("auctions", "status", lot)).isEqualTo("ENDED");
        assertThat(market.column("auctions", "winner_id", lot)).isEqualTo(buyer.id().toString());
        assertThat(market.column("auctions", "current_price", lot)).isEqualTo("18000000.00");
        assertThat(market.column("auctions", "bought_now", lot)).isEqualTo("false");
        assertThat(jdbc.queryForObject("SELECT amount::text FROM payments WHERE auction_id = ?::uuid", String.class, lot))
                .isEqualTo("18000000.00");
    }

    @Test
    void theSellerAcceptsAndOtherOffersAreAnswered() throws Exception {
        String lot = buyNowLot("Two offers");
        Account first = market.account("offer.first", RoleName.BUYER);
        Account second = market.account("offer.second", RoleName.BUYER);
        String low = market.data(offer(first, lot, 12_000_000)).get("id").asString();
        String high = market.data(offer(second, lot, 16_000_000)).get("id").asString();

        market.as(first, post("/api/seller/lot-offers/" + high + "/accept")).andExpect(status().isForbidden());
        market.as(seller, post("/api/seller/lot-offers/" + high + "/accept"))
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
        market.as(first, get("/api/users/me/lot-offers")).andExpect(jsonPath("$.data[0].id").value(low))
                .andExpect(jsonPath("$.data[0].status").value("DECLINED"));
        assertThat(market.column("auctions", "winner_id", lot)).isEqualTo(second.id().toString());
    }

    @Test
    void aBidClosesOffersAndEachBuyerHasThree() throws Exception {
        String lot = buyNowLot("Limited offers");
        Account buyer = market.account("offer.limit", RoleName.BUYER);
        String one = market.data(offer(buyer, lot, 11_000_000)).get("id").asString();
        market.as(seller, post("/api/seller/lot-offers/" + one + "/decline"))
                .andExpect(jsonPath("$.data.status").value("DECLINED"));
        String two = market.data(offer(buyer, lot, 12_000_000)).get("id").asString();
        // EN: A new offer replaces the open one. / VI: Đề nghị mới thay cho đề nghị còn mở.
        String three = market.data(offer(buyer, lot, 13_000_000)).get("id").asString();
        assertThat(market.column("lot_offers", "status", two)).isEqualTo("WITHDRAWN");
        offer(buyer, lot, 14_000_000).andExpect(jsonPath("$.code").value("OFFER_LIMIT"));

        Account bidder = market.account("offer.bidder", RoleName.BUYER);
        market.bid(bidder, lot, 10_000_000).andExpect(status().isCreated());
        market.as(seller, post("/api/seller/lot-offers/" + three + "/accept"))
                .andExpect(jsonPath("$.code").value("OFFER_UNAVAILABLE"));
        offer(market.account("offer.late", RoleName.BUYER), lot, 15_000_000)
                .andExpect(jsonPath("$.code").value("OFFER_UNAVAILABLE"));
    }
}
