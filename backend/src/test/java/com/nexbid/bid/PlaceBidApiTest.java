package com.nexbid.bid;

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

import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Placing a bid (guide §20) — the five cases the guide lists, plus the ones that only show up over
 *     HTTP: who may call it at all, and what happens to an id that does not exist.
 * VI: Đặt giá (guide §20) — năm trường hợp guide liệt kê, cộng những thứ chỉ lộ ra khi đi qua HTTP: ai
 *     được phép gọi, và chuyện gì xảy ra với một id không tồn tại.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class PlaceBidApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

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

    /** EN: An approved lot, still scheduled. / VI: Một lô đã duyệt, còn đang chờ tới giờ. */
    private String approvedLot(String seller, String admin, String name) throws Exception {
        String categories = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();

        String productBody = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(name, categoryId)))
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

        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();

        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit")
                .header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve")
                .header("Authorization", "Bearer " + admin));

        return auctionId;
    }

    /**
     * EN: Opens the lot for bidding. The scheduler that would do this arrives at a later function.
     * VI: Mở lô cho việc trả giá. Scheduler làm việc này sẽ có ở chức năng sau.
     */
    private void openNow(String auctionId) {
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 minute' "
                + "WHERE id = ?::uuid", auctionId);
    }

    private org.springframework.test.web.servlet.ResultActions bid(
            String auctionId, String token, String amount) throws Exception {

        return mockMvc.perform(post("/api/auctions/" + auctionId + "/bids")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    @Test
    void aValidBidIsAcceptedAndMovesThePrice() throws Exception {
        String seller = tokenFor("bid.seller1@nexbid.com", "Bid Seller", RoleName.SELLER);
        String admin = tokenFor("bid.admin1@nexbid.com", "Bid Admin", RoleName.ADMIN);
        String bidder = tokenFor("bid.buyer1@nexbid.com", "Nguyen Van A", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "Open lot");
        openNow(auction);

        bid(auction, bidder, "10000000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bid.amount").value(10000000))
                .andExpect(jsonPath("$.data.bid.mine").value(true))
                .andExpect(jsonPath("$.data.currentPrice").value(10000000))
                .andExpect(jsonPath("$.data.bidCount").value(1))
                // EN: The page must be told the new floor, not left to recompute it.
                // VI: Trang phải được báo mức sàn mới, không phải tự tính lại.
                .andExpect(jsonPath("$.data.minimumNextBid").value(10500000))
                .andExpect(jsonPath("$.data.serverTime").isNotEmpty());

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM bids WHERE auction_id = ?::uuid", Integer.class, auction);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void theFirstBidOnlyHasToMeetTheOpeningPrice() throws Exception {
        String seller = tokenFor("bid.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("bid.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String bidder = tokenFor("bid.buyer2@nexbid.com", "Second Buyer", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "First bid lot");
        openNow(auction);

        // EN: Exactly the starting price, not one increment above it — nobody should have to beat an
        //     offer no one has made.
        // VI: Đúng bằng giá khởi điểm, không phải cộng thêm một bước — không ai phải vượt qua một mức giá
        //     chưa ai đưa ra.
        bid(auction, bidder, "10000000").andExpect(status().isCreated());
    }

    @Test
    void aSecondBidMustClearTheCurrentPriceByAFullIncrement() throws Exception {
        String seller = tokenFor("bid.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String admin = tokenFor("bid.admin3@nexbid.com", "Third Admin", RoleName.ADMIN);
        String first = tokenFor("bid.buyer3a@nexbid.com", "Buyer Three A", RoleName.BUYER);
        String second = tokenFor("bid.buyer3b@nexbid.com", "Buyer Three B", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "Two bids lot");
        openNow(auction);

        bid(auction, first, "10000000").andExpect(status().isCreated());

        bid(auction, second, "10400000")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BID_TOO_LOW"));

        bid(auction, second, "10500000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bidCount").value(2))
                .andExpect(jsonPath("$.data.minimumNextBid").value(11000000));
    }

    @Test
    void aBidBelowTheOpeningPriceIsRefused() throws Exception {
        String seller = tokenFor("bid.seller4@nexbid.com", "Fourth Seller", RoleName.SELLER);
        String admin = tokenFor("bid.admin4@nexbid.com", "Fourth Admin", RoleName.ADMIN);
        String bidder = tokenFor("bid.buyer4@nexbid.com", "Fourth Buyer", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "Low bid lot");
        openNow(auction);

        bid(auction, bidder, "9999999")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BID_TOO_LOW"));

        // EN: A refused bid must leave no trace; otherwise the history shows offers nobody made.
        // VI: Lượt bị từ chối không được để lại dấu vết; nếu không, lịch sử sẽ hiện những lượt không ai trả.
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM bids WHERE auction_id = ?::uuid", Integer.class, auction);
        assertThat(rows).isZero();
    }

    @Test
    void aSellerMayNotBidOnTheirOwnLot() throws Exception {
        String seller = tokenFor("bid.seller5@nexbid.com", "Fifth Seller", RoleName.SELLER);
        String admin = tokenFor("bid.admin5@nexbid.com", "Fifth Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "Own lot");
        openNow(auction);

        // EN: Spec §8 — bidding up your own lot is how a seller invents demand that is not there.
        // VI: Spec §8 — tự trả giá lô của mình là cách người bán tạo ra nhu cầu không có thật.
        bid(auction, seller, "10000000")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_CANNOT_BID"));
    }

    @Test
    void aLotWhoseClockRanOutTakesNoMoreBids() throws Exception {
        String seller = tokenFor("bid.seller6@nexbid.com", "Sixth Seller", RoleName.SELLER);
        String admin = tokenFor("bid.admin6@nexbid.com", "Sixth Admin", RoleName.ADMIN);
        String bidder = tokenFor("bid.buyer6@nexbid.com", "Sixth Buyer", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "Expired lot");

        // EN: Still marked ACTIVE — the gap between the clock running out and the scheduler noticing is
        //     exactly where a late bid would otherwise slip through.
        // VI: Vẫn đang là ACTIVE — khoảng giữa lúc hết giờ và lúc scheduler nhận ra chính là chỗ một lượt
        //     trả giá muộn có thể lọt qua.
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '2 hours', "
                + "end_time = now() - interval '1 minute' WHERE id = ?::uuid", auction);

        bid(auction, bidder, "10000000")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_ALREADY_ENDED"));
    }

    @Test
    void aLotThatHasNotStartedTakesNoBids() throws Exception {
        String seller = tokenFor("bid.seller7@nexbid.com", "Seventh Seller", RoleName.SELLER);
        String admin = tokenFor("bid.admin7@nexbid.com", "Seventh Admin", RoleName.ADMIN);
        String bidder = tokenFor("bid.buyer7@nexbid.com", "Seventh Buyer", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "Scheduled lot");

        bid(auction, bidder, "10000000")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_ACTIVE"));
    }

    @Test
    void anAnonymousCallerCannotBid() throws Exception {
        String seller = tokenFor("bid.seller8@nexbid.com", "Eighth Seller", RoleName.SELLER);
        String admin = tokenFor("bid.admin8@nexbid.com", "Eighth Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "Public to read");
        openNow(auction);

        // EN: The lot itself is public, so this asserts the carve-out in SecurityConfig actually holds.
        // VI: Bản thân lô là công khai, nên phép thử này khẳng định phần khoét ra trong SecurityConfig
        //     thật sự có hiệu lực.
        mockMvc.perform(get("/api/auctions/" + auction)).andExpect(status().isOk());

        mockMvc.perform(post("/api/auctions/" + auction + "/bids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000000}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
    }

    @Test
    void anUnknownLotIs404() throws Exception {
        String bidder = tokenFor("bid.buyer9@nexbid.com", "Ninth Buyer", RoleName.BUYER);

        bid(UUID.randomUUID().toString(), bidder, "10000000")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }

    @Test
    void anAmountOfZeroIsRejectedBeforeAnyRuleRuns() throws Exception {
        String bidder = tokenFor("bid.buyer10@nexbid.com", "Tenth Buyer", RoleName.BUYER);

        bid(UUID.randomUUID().toString(), bidder, "0")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void theBidderNameIsMaskedInTheResponse() throws Exception {
        String seller = tokenFor("bid.seller11@nexbid.com", "Eleventh Seller", RoleName.SELLER);
        String admin = tokenFor("bid.admin11@nexbid.com", "Eleventh Admin", RoleName.ADMIN);
        String bidder = tokenFor("bid.buyer11@nexbid.com", "Pinnacle Buyer", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "Masked lot");
        openNow(auction);

        bid(auction, bidder, "10000000")
                .andExpect(jsonPath("$.data.bid.bidderMask").value("pin***"));
    }
}
