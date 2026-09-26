package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The server owns the clock (guide §24, spec §11). A countdown drawn from the visitor's own machine is
 *     wrong for anyone whose clock drifts, and convenient for anyone who sets theirs back on purpose.
 * VI: Đồng hồ thuộc về server (guide §24, spec §11). Đồng hồ đếm ngược vẽ theo máy khách sẽ sai với bất kỳ
 *     ai bị lệch giờ, và rất tiện cho người cố tình vặn ngược giờ máy mình.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class ServerClockTest {

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

    @Test
    void anyoneCanAskWhatTimeTheServerThinksItIs() throws Exception {
        Instant before = Instant.now();

        String body = mockMvc.perform(get("/api/server-time"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Instant after = Instant.now();
        Instant serverTime = Instant.parse(objectMapper.readTree(body).get("data").get("serverTime").asString());

        assertThat(serverTime).isBetween(before, after);
    }

    @Test
    void theClockDoesNotNeedAToken() throws Exception {
        // EN: A visitor who has not signed in still watches a countdown, so this must answer without one.
        // VI: Khách chưa đăng nhập vẫn nhìn đồng hồ đếm ngược, nên chỗ này phải trả lời khi không có token.
        mockMvc.perform(get("/api/server-time")).andExpect(status().isOk());
    }

    @Test
    void timesTravelAsIsoTextNotAsNumbers() throws Exception {
        String seller = tokenFor("clock.seller1@nexbid.com", "Clock Seller", RoleName.SELLER);
        String admin = tokenFor("clock.admin1@nexbid.com", "Clock Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "Timed lot");

        // EN: The whole countdown is endTime minus serverTime. If either arrived as an epoch number the
        //     arithmetic would still run and quietly produce a wrong clock, so the format is pinned here.
        // VI: Toàn bộ đồng hồ đếm ngược là endTime trừ serverTime. Nếu một trong hai về dạng số epoch thì
        //     phép trừ vẫn chạy và lặng lẽ ra đồng hồ sai, nên khoá chặt định dạng ở đây.
        mockMvc.perform(get("/api/auctions/" + auction))
                .andExpect(jsonPath("$.data.serverTime").isString())
                .andExpect(jsonPath("$.data.auction.endTime").isString())
                .andExpect(jsonPath("$.data.auction.startTime").isString())
                .andExpect(jsonPath("$.data.serverTime", org.hamcrest.Matchers.endsWith("Z")))
                .andExpect(jsonPath("$.data.auction.endTime", org.hamcrest.Matchers.endsWith("Z")));

        mockMvc.perform(get("/api/server-time"))
                .andExpect(jsonPath("$.data.serverTime").isString())
                .andExpect(jsonPath("$.data.serverTime", org.hamcrest.Matchers.endsWith("Z")));
    }

    @Test
    void theClockNeverRunsBackwards() throws Exception {
        Instant first = readClock();
        Instant second = readClock();

        // EN: An offset worked out from a clock that jumps back would make a countdown jump forward.
        // VI: Độ lệch tính từ một đồng hồ nhảy lùi sẽ khiến đồng hồ đếm ngược nhảy tới.
        assertThat(second).isAfterOrEqualTo(first);
    }

    @Test
    void theLotPageAndTheClockEndpointAgree() throws Exception {
        String seller = tokenFor("clock.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("clock.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "Agreeing lot");

        Instant fromClock = readClock();

        String body = mockMvc.perform(get("/api/auctions/" + auction))
                .andReturn().getResponse().getContentAsString();
        Instant fromPage = Instant.parse(objectMapper.readTree(body).get("data").get("serverTime").asString());

        Instant afterwards = readClock();

        // EN: Two endpoints, one clock. If they drifted apart, a page that resynced would jump.
        // VI: Hai endpoint, một đồng hồ. Nếu chúng lệch nhau, trang nào đồng bộ lại sẽ bị nhảy giờ.
        assertThat(fromPage).isBetween(fromClock, afterwards);
    }

    @Test
    void theServerDecidesTheLotIsOverNoMatterWhatTheCallerThinks() throws Exception {
        String seller = tokenFor("clock.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String admin = tokenFor("clock.admin3@nexbid.com", "Third Admin", RoleName.ADMIN);
        String buyer = tokenFor("clock.buyer3@nexbid.com", "Third Buyer", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "Closing lot");

        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '2 hours', "
                + "end_time = now() - interval '1 second' WHERE id = ?::uuid", auction);

        // EN: Guide §24 is explicit that the browser countdown is decoration. A page whose clock is a
        //     minute slow still shows time left — and the bid it sends is still refused.
        // VI: Guide §24 nói rõ đồng hồ trên trình duyệt chỉ để hiển thị. Trang có đồng hồ chậm một phút vẫn
        //     hiện là còn giờ — và lượt trả giá nó gửi lên vẫn bị từ chối.
        mockMvc.perform(post("/api/auctions/" + auction + "/bids")
                        .header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_ALREADY_ENDED"));

        mockMvc.perform(get("/api/auctions/" + auction))
                .andExpect(jsonPath("$.data.openForBidding").value(false));
    }

    private Instant readClock() throws Exception {
        String body = mockMvc.perform(get("/api/server-time"))
                .andReturn().getResponse().getContentAsString();

        return Instant.parse(objectMapper.readTree(body).get("data").get("serverTime").asString());
    }
}
