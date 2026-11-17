package com.nexbid.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

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
import com.nexbid.fx.ExchangeRates;
import com.nexbid.support.Market;
import com.nexbid.support.Market.Account;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Taking a bid back — when it is allowed, what the lot goes back to, and the staff log — and the reference
 *     exchange rates.
 * VI: Rút lại lượt trả giá — khi nào được phép, lô trở về đâu, và nhật ký cho nhân viên — cùng tỷ giá tham khảo.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class RetractionAndFxApiTest {

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
    @Autowired
    private ExchangeRates rates;

    private Market market;
    private Account seller;
    private Account admin;

    @BeforeEach
    void setUp() throws Exception {
        market = new Market(mockMvc, users, auctions, jdbc, json);
        seller = market.account("retract.seller", RoleName.SELLER);
        admin = market.account("retract.admin", RoleName.ADMIN);
    }

    private ResultActions retract(Account who, String lot, String reason) throws Exception {
        return market.as(who, post("/api/auctions/" + lot + "/bids/retract").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\",\"note\":\"meant something else\"}"));
    }

    private void age(String lot, Account who, String interval) {
        jdbc.update("UPDATE bids SET created_at = created_at - ?::interval WHERE auction_id = ?::uuid AND bidder_id = ?",
                interval, lot, who.id());
    }

    @Test
    void aQuickRetractionPutsTheLotBackAndIsLogged() throws Exception {
        String lot = market.openLot(seller, admin, "Retract lot", 10_000_000, "");
        Account first = market.account("retract.first", RoleName.BUYER);
        Account second = market.account("retract.second", RoleName.BUYER);
        market.bid(first, lot, 10_000_000).andExpect(status().isCreated());
        market.bid(second, lot, 11_000_000).andExpect(status().isCreated());
        market.bid(second, lot, 12_000_000).andExpect(status().isCreated());

        market.as(second, get("/api/auctions/" + lot + "/bids/retraction"))
                .andExpect(jsonPath("$.data.quick").value(true))
                .andExpect(jsonPath("$.data.typo").value(false))
                .andExpect(jsonPath("$.data.blockedBy").doesNotExist());
        market.as(second, post("/api/auctions/" + lot + "/auto-bid").contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxAmount\":20000000}")).andExpect(status().is2xxSuccessful());

        retract(second, lot, "QUICK").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bidsRemoved").value(2))
                .andExpect(jsonPath("$.data.currentPrice").value(10000000))
                .andExpect(jsonPath("$.data.bidCount").value(1));

        assertThat(market.column("auctions", "leading_bidder_id", lot)).isEqualTo(first.id().toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auto_bids WHERE auction_id = ?::uuid AND user_id = ? AND active",
                Integer.class, lot, second.id())).isZero();
        // EN: The bids are gone, so the next bid starts from the restored price. / VI: Các lượt đã mất, nên lượt sau tính từ giá được khôi phục.
        market.bid(second, lot, 10_500_000).andExpect(status().isCreated());

        market.as(admin, get("/api/admin/bid-retractions"))
                .andExpect(jsonPath("$.data[0].auctionId").value(lot))
                .andExpect(jsonPath("$.data[0].reason").value("QUICK"))
                .andExpect(jsonPath("$.data[0].amount").value(12000000))
                .andExpect(jsonPath("$.data[0].wasLeading").value(true))
                .andExpect(jsonPath("$.data[0].priceAfter").value(10000000));
        Account moderator = market.account("retract.mod", RoleName.MODERATOR);
        market.as(moderator, get("/api/admin/bid-retractions")).andExpect(status().isOk());
        market.as(first, get("/api/admin/bid-retractions")).andExpect(status().isForbidden());
    }

    @Test
    void afterTheFirstMinutesOnlyAnObviousTypoCanBeTakenBack() throws Exception {
        String lot = market.openLot(seller, admin, "Typo lot", 10_000_000, "");
        Account steady = market.account("retract.steady", RoleName.BUYER);
        Account slip = market.account("retract.slip", RoleName.BUYER);
        market.bid(steady, lot, 10_000_000).andExpect(status().isCreated());
        market.bid(steady, lot, 11_000_000).andExpect(status().isCreated());
        age(lot, steady, "1 hour");
        retract(steady, lot, "QUICK").andExpect(jsonPath("$.code").value("RETRACTION_NOT_ALLOWED"));
        retract(steady, lot, "TYPO").andExpect(jsonPath("$.code").value("RETRACTION_NOT_ALLOWED"));

        market.bid(slip, lot, 110_000_000).andExpect(status().isCreated());
        age(lot, slip, "30 minutes");
        market.as(slip, get("/api/auctions/" + lot + "/bids/retraction"))
                .andExpect(jsonPath("$.data.quick").value(false))
                .andExpect(jsonPath("$.data.typo").value(true));
        retract(slip, lot, "QUICK").andExpect(jsonPath("$.code").value("RETRACTION_NOT_ALLOWED"));
        retract(slip, lot, "TYPO").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentPrice").value(11000000));
    }

    @Test
    void notInTheLastMinutesAndNotTooOften() throws Exception {
        String lot = market.openLot(seller, admin, "Late lot", 10_000_000, "");
        Account bidder = market.account("retract.late", RoleName.BUYER);
        market.bid(bidder, lot, 10_000_000).andExpect(status().isCreated());
        jdbc.update("UPDATE auctions SET end_time = now() + interval '5 minutes' WHERE id = ?::uuid", lot);
        retract(bidder, lot, "QUICK").andExpect(jsonPath("$.code").value("RETRACTION_TOO_LATE"));
        // EN: Back to hours away, so no later test's close sweep ends it. / VI: Trả về còn nhiều giờ, để lần quét đóng lô của test sau không đóng nó.
        jdbc.update("UPDATE auctions SET end_time = now() + interval '4 hours' WHERE id = ?::uuid", lot);

        String other = market.openLot(seller, admin, "Limit lot", 10_000_000, "");
        market.bid(bidder, other, 10_000_000).andExpect(status().isCreated());
        for (int i = 0; i < 3; i++) {
            jdbc.update("""
                    INSERT INTO bid_retractions (id, auction_id, bidder_id, amount, bids_removed, bid_placed_at, reason,
                                                 price_before, price_after, was_leading)
                    VALUES (?, ?::uuid, ?, 1, 1, now(), 'QUICK', 1, 1, false)
                    """, UUID.randomUUID(), other, bidder.id());
        }
        market.as(bidder, get("/api/auctions/" + other + "/bids/retraction"))
                .andExpect(jsonPath("$.data.usedThisMonth").value(3))
                .andExpect(jsonPath("$.data.blockedBy").value("RETRACTION_LIMIT"));
        retract(bidder, other, "QUICK").andExpect(jsonPath("$.code").value("RETRACTION_LIMIT"));
    }

    @Test
    void aSealedBidTakenBackCanBePlacedAgain() throws Exception {
        String lot = market.openLot(seller, admin, "Sealed retract", 10_000_000, "\"sealed\":true");
        Account bidder = market.account("retract.sealed", RoleName.BUYER);
        market.bid(bidder, lot, 99_000_000).andExpect(status().isCreated());
        retract(bidder, lot, "QUICK").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bidCount").value(0));
        market.bid(bidder, lot, 12_000_000).andExpect(status().isCreated());
        assertThat(market.column("auctions", "current_price", lot)).isEqualTo("12000000.00");
    }

    @Test
    void ratesArePublicAndFollowTheFeed() throws Exception {
        mockMvc.perform(get("/api/fx")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.base").value("VND"))
                .andExpect(jsonPath("$.data.rates[0].currency").value("USD"))
                .andExpect(jsonPath("$.data.rates[1].currency").value("EUR"));

        rates.store(json.readTree("""
                {"result":"success","base_code":"USD","rates":{"USD":1,"VND":25000,"EUR":0.8}}
                """), Instant.now());
        mockMvc.perform(get("/api/fx"))
                .andExpect(jsonPath("$.data.rates[0].vndPer").value(25000))
                .andExpect(jsonPath("$.data.rates[1].vndPer").value(31250))
                .andExpect(jsonPath("$.data.source").value("open.er-api.com"));
    }
}
