package com.nexbid.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.nexbid.auction.AuctionService;
import com.nexbid.support.Market;
import com.nexbid.support.Market.Account;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Sealed-bid lots keep every bid hidden until the close; watchers hear about a cheaper relist; chat replies can be
 *     saved; new photos wait in the review queue.
 * VI: Lô đấu giá kín giữ mọi lượt giá ẩn tới khi đóng; người theo dõi được báo khi lô đăng lại rẻ hơn; câu trả lời chat
 *     lưu sẵn được; ảnh mới chờ trong hàng duyệt.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class SealedAndExtrasApiTest {

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
        seller = market.account("sealed.seller", RoleName.SELLER);
        admin = market.account("sealed.admin", RoleName.ADMIN);
    }

    @Test
    void sealedBidsStayHiddenAndTheHighestWins() throws Exception {
        String product = market.product(seller, "Sealed lot");
        market.as(seller, post("/api/seller/auctions").contentType(MediaType.APPLICATION_JSON).content("""
                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,"startTime":"%s","endTime":"%s",
                 "sealed":true,"buyNowPrice":20000000}
                """.formatted(product, java.time.Instant.now().plusSeconds(1800), java.time.Instant.now().plusSeconds(7200))))
                .andExpect(jsonPath("$.code").value("AUCTION_PRICES_INVALID"));
        String lot = market.openLot(seller, admin, "Sealed lot two", 10_000_000, "\"sealed\":true,\"antiSnipingEnabled\":true");

        Account low = market.account("sealed.low", RoleName.BUYER);
        Account high = market.account("sealed.high", RoleName.BUYER);
        Account late = market.account("sealed.late", RoleName.BUYER);
        market.bid(low, lot, 9_000_000).andExpect(jsonPath("$.code").value("BID_TOO_LOW"));
        market.bid(low, lot, 12_000_000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currentPrice").value(10000000))
                .andExpect(jsonPath("$.data.leading").value(false));
        market.bid(high, lot, 15_000_000).andExpect(status().isCreated());
        // EN: Below the leader, still taken; nobody is told. / VI: Thấp hơn người dẫn đầu vẫn được nhận; không ai được báo.
        market.bid(late, lot, 11_000_000).andExpect(status().isCreated());
        market.bid(low, lot, 16_000_000).andExpect(jsonPath("$.code").value("SEALED_BID_EXISTS"));
        market.as(low, post("/api/auctions/" + lot + "/auto-bid").contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxAmount\":20000000}")).andExpect(jsonPath("$.code").value("SEALED_LOT"));

        mockMvc.perform(get("/api/auctions/" + lot))
                .andExpect(jsonPath("$.data.auction.currentPrice").value(10000000))
                .andExpect(jsonPath("$.data.auction.sealed").value(true))
                .andExpect(jsonPath("$.data.auction.bidCount").value(3))
                .andExpect(jsonPath("$.data.minimumNextBid").value(10000000));
        market.as(seller, get("/api/seller/auctions/" + lot)).andExpect(jsonPath("$.data.currentPrice").value(10000000));
        market.as(low, get("/api/auctions/" + lot + "/bids"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].amount").value(12000000));
        mockMvc.perform(get("/api/auctions/" + lot + "/bids")).andExpect(jsonPath("$.data.items.length()").value(0));
        market.as(high, get("/api/users/me/bids"))
                .andExpect(jsonPath("$.data[?(@.auction.auction.id == '" + lot + "')].standing").value(
                        org.hamcrest.Matchers.hasItem("SEALED")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'OUTBID'",
                Integer.class, low.id())).isZero();

        market.close(lot);
        mockMvc.perform(get("/api/auctions/" + lot)).andExpect(jsonPath("$.data.auction.currentPrice").value(15000000));
        assertThat(market.column("auctions", "winner_id", lot)).isEqualTo(high.id().toString());
        mockMvc.perform(get("/api/auctions/" + lot + "/bids")).andExpect(jsonPath("$.data.items.length()").value(3));
    }

    @Test
    void watchersHearAboutACheaperRelist() throws Exception {
        String lot = market.openLot(seller, admin, "Watched relist", 10_000_000, "\"autoRelist\":1,\"relistDropPercent\":20");
        Account watcher = market.account("sealed.watcher", RoleName.BUYER);
        market.as(watcher, post("/api/auctions/" + lot + "/watch")).andExpect(status().is2xxSuccessful());
        market.close(lot);
        String next = jdbc.queryForObject("SELECT id::text FROM auctions WHERE relisted_from = ?::uuid", String.class, lot);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM watchlists WHERE user_id = ? AND auction_id = ?::uuid",
                Integer.class, watcher.id(), next)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT message FROM notifications WHERE user_id = ? AND type = 'LOT_RELISTED'
                """, String.class, watcher.id())).contains("8,000,000 VND").contains("10,000,000 VND");
    }

    @Test
    void quickRepliesAreKeptPerPerson() throws Exception {
        Account person = market.account("sealed.replies", RoleName.BUYER);
        String id = market.data(market.as(person, post("/api/users/me/quick-replies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Shipped today, tracking to follow.\"}"))).get("id").asString();
        for (int i = 1; i < 10; i++) {
            market.as(person, post("/api/users/me/quick-replies").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"body\":\"Reply " + i + "\"}")).andExpect(status().isOk());
        }
        market.as(person, post("/api/users/me/quick-replies").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"One too many\"}")).andExpect(jsonPath("$.code").value("QUICK_REPLY_LIMIT"));
        market.as(seller, delete("/api/users/me/quick-replies/" + id)).andExpect(status().isNotFound());
        market.as(person, delete("/api/users/me/quick-replies/" + id)).andExpect(status().isOk());
        market.as(person, get("/api/users/me/quick-replies")).andExpect(jsonPath("$.data.length()").value(9));
    }

    @Test
    void newPhotosWaitForReviewAndCanBeTakenDown() throws Exception {
        String product = market.product(seller, "Photo review");
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/seller/products/" + product + "/images")
                .file(new org.springframework.mock.web.MockMultipartFile("files", "a.png", "image/png", png))
                .file(new org.springframework.mock.web.MockMultipartFile("files", "b.png", "image/png", png))
                .header("Authorization", "Bearer " + seller.token()))
                .andExpect(status().is2xxSuccessful());
        var queue = market.data(market.as(admin, get("/api/admin/media")));
        java.util.List<String> mine = new java.util.ArrayList<>();
        queue.forEach(item -> {
            if (item.get("productId").asString().equals(product)) {
                mine.add(item.get("id").asString());
            }
        });
        assertThat(mine).hasSizeGreaterThanOrEqualTo(1);

        market.as(seller, get("/api/admin/media")).andExpect(status().isForbidden());
        market.as(admin, post("/api/admin/media/IMAGE/" + mine.getFirst() + "/remove").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Shows a person's face\"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product_images WHERE id = ?::uuid", Integer.class,
                mine.getFirst())).isZero();
        assertThat(jdbc.queryForObject("SELECT message FROM notifications WHERE user_id = ? AND type = 'MEDIA_REMOVED'",
                String.class, seller.id())).contains("Shows a person's face");
        if (mine.size() > 1) {
            market.as(admin, post("/api/admin/media/IMAGE/" + mine.get(1) + "/keep")).andExpect(status().isOk());
        }
        market.as(admin, post("/api/admin/media/IMAGE/" + UUID.randomUUID() + "/keep")).andExpect(status().isNotFound());
    }

    @Test
    void suggestionsAreCachedBriefly() throws Exception {
        String tag = Long.toString(System.nanoTime(), 36);
        String query = "Cachedlot " + tag;
        mockMvc.perform(get("/api/auctions/suggest").param("q", query)).andExpect(jsonPath("$.data.lots.length()").value(0));
        market.openLot(seller, admin, "Cachedlot " + tag, 10_000_000, null);
        // EN: Still the cached answer for a few seconds. / VI: Vẫn là câu trả lời đã cache trong vài giây.
        mockMvc.perform(get("/api/auctions/suggest").param("q", query)).andExpect(jsonPath("$.data.lots.length()").value(0));
        mockMvc.perform(get("/api/auctions/suggest").param("q", query.toUpperCase() + " "))
                .andExpect(jsonPath("$.data.lots.length()").value(0));
    }
}
