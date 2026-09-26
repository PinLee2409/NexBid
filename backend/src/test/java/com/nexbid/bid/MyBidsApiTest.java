package com.nexbid.bid;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nexbid.auction.AuctionService;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: `GET /api/users/me/bids` (spec §27): the lots a buyer bid on, their best bid, and where they stand.
 * VI: `GET /api/users/me/bids` (spec §27): các lô người mua đã trả giá, lượt cao nhất và vị thế của họ.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class MyBidsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private AuctionService auctions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private String openLot(String seller, String admin, String name) throws Exception {
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
        String auction = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auction + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auction);
        return auction;
    }

    private void bid(String token, String auction, long amount) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auction + "/bids").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    private ResultActions myBids(String token) throws Exception {
        return mockMvc.perform(get("/api/users/me/bids").header("Authorization", "Bearer " + token));
    }

    @Test
    void eachLotShowsTheBestBidAndWhereTheBidderStands() throws Exception {
        String seller = tokenFor("mybids.seller@nexbid.com", "MyBids Seller", RoleName.SELLER);
        String admin = tokenFor("mybids.admin@nexbid.com", "MyBids Admin", RoleName.ADMIN);
        String anna = tokenFor("mybids.anna@nexbid.com", "Anna MyBids", RoleName.BUYER);
        String ben = tokenFor("mybids.ben@nexbid.com", "Ben MyBids", RoleName.BUYER);
        String contested = openLot(seller, admin, "Contested camera");
        String quiet = openLot(seller, admin, "Quiet camera");

        bid(anna, contested, 10_000_000);
        bid(ben, contested, 10_500_000);
        bid(anna, quiet, 10_000_000);

        // EN: Latest activity first; the leader is never named, but Anna may know it is not her.
        // VI: Hoạt động gần nhất lên trước; không nêu tên người dẫn, nhưng Anna được biết đó không phải mình.
        myBids(anna)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].auction.auction.id").value(quiet))
                .andExpect(jsonPath("$.data[0].standing").value("WINNING"))
                .andExpect(jsonPath("$.data[1].auction.product.name").value("Contested camera"))
                .andExpect(jsonPath("$.data[1].yourBid").value(10_000_000))
                .andExpect(jsonPath("$.data[1].standing").value("OUTBID"));
        myBids(ben)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].standing").value("WINNING"));

        // EN: Once the lot closes, the standings become the result.
        // VI: Khi lô đóng, vị thế trở thành kết quả.
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", contested);
        auctions.endDueAuctions(Instant.now(), 100);

        myBids(ben).andExpect(jsonPath("$.data[0].standing").value("WON"));
        myBids(anna).andExpect(jsonPath("$.data[?(@.auction.auction.id == '" + contested + "')].standing")
                .value("LOST"));
    }

    @Test
    void someoneWhoNeverBidHasAnEmptyList() throws Exception {
        String idle = tokenFor("mybids.idle@nexbid.com", "Idle Buyer", RoleName.BUYER);

        myBids(idle).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/users/me/bids")).andExpect(status().isUnauthorized());
    }
}
