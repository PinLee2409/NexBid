package com.nexbid.watchlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
 * EN: Watchlists (guide §28, spec §15).
 * VI: Danh sách theo dõi (guide §28, spec §15).
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class WatchlistApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WatchlistService watchlist;

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

    /** EN: A lot submitted for review, not yet decided. / VI: Một lô đã gửi duyệt, chưa có quyết định. */
    private String submittedLot(String seller, String name) throws Exception {
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

        return auctionId;
    }

    private String approvedLot(String seller, String admin, String name) throws Exception {
        String auctionId = submittedLot(seller, name);
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve")
                .header("Authorization", "Bearer " + admin));
        return auctionId;
    }

    private org.springframework.test.web.servlet.ResultActions watch(String token, String auctionId)
            throws Exception {
        return mockMvc.perform(post("/api/auctions/" + auctionId + "/watch")
                .header("Authorization", "Bearer " + token));
    }

    private org.springframework.test.web.servlet.ResultActions unwatch(String token, String auctionId)
            throws Exception {
        return mockMvc.perform(delete("/api/auctions/" + auctionId + "/watch")
                .header("Authorization", "Bearer " + token));
    }

    private org.springframework.test.web.servlet.ResultActions myList(String token) throws Exception {
        return mockMvc.perform(get("/api/users/me/watchlist").header("Authorization", "Bearer " + token));
    }

    private int rowsFor(String auctionId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM watchlists WHERE auction_id = ?::uuid", Integer.class, auctionId);
    }

    @Test
    void aWatchedLotShowsUpOnTheWatchlist() throws Exception {
        String seller = tokenFor("watch.seller1@nexbid.com", "Watch Seller", RoleName.SELLER);
        String admin = tokenFor("watch.admin1@nexbid.com", "Watch Admin", RoleName.ADMIN);
        String buyer = tokenFor("watch.buyer1@nexbid.com", "Watch Buyer", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "Leica M6");

        watch(buyer, auction)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auctionId").value(auction))
                .andExpect(jsonPath("$.data.watching").value(true));

        myList(buyer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].auction.id").value(auction))
                .andExpect(jsonPath("$.data[0].product.name").value("Leica M6"))
                .andExpect(jsonPath("$.data[0].seller.displayName").value("Watch Seller"));
    }

    @Test
    void tappingTwiceStillMeansOneWatch() throws Exception {
        String seller = tokenFor("watch.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("watch.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String buyer = tokenFor("watch.buyer2@nexbid.com", "Second Buyer", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "Double tap");

        watch(buyer, auction).andExpect(status().isOk());
        watch(buyer, auction).andExpect(status().isOk()).andExpect(jsonPath("$.data.watching").value(true));

        assertThat(rowsFor(auction)).isEqualTo(1);
    }

    @Test
    void unwatchingRemovesItAndDoingItAgainIsHarmless() throws Exception {
        String seller = tokenFor("watch.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String admin = tokenFor("watch.admin3@nexbid.com", "Third Admin", RoleName.ADMIN);
        String buyer = tokenFor("watch.buyer3@nexbid.com", "Third Buyer", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "Changed my mind");

        watch(buyer, auction);

        unwatch(buyer, auction)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.watching").value(false));
        unwatch(buyer, auction)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.watching").value(false));

        myList(buyer).andExpect(jsonPath("$.data.length()").value(0));
        assertThat(rowsFor(auction)).isZero();
    }

    @Test
    void theListIsMostRecentlyWatchedFirst() throws Exception {
        String seller = tokenFor("watch.seller4@nexbid.com", "Fourth Seller", RoleName.SELLER);
        String admin = tokenFor("watch.admin4@nexbid.com", "Fourth Admin", RoleName.ADMIN);
        String buyer = tokenFor("watch.buyer4@nexbid.com", "Fourth Buyer", RoleName.BUYER);
        String first = approvedLot(seller, admin, "Watched first");
        String second = approvedLot(seller, admin, "Watched second");
        String third = approvedLot(seller, admin, "Watched third");

        watch(buyer, first);
        Thread.sleep(20);
        watch(buyer, second);
        Thread.sleep(20);
        watch(buyer, third);

        myList(buyer)
                .andExpect(jsonPath("$.data[0].auction.id").value(third))
                .andExpect(jsonPath("$.data[1].auction.id").value(second))
                .andExpect(jsonPath("$.data[2].auction.id").value(first));
    }

    @Test
    void eachPersonSeesOnlyTheirOwnList() throws Exception {
        String seller = tokenFor("watch.seller5@nexbid.com", "Fifth Seller", RoleName.SELLER);
        String admin = tokenFor("watch.admin5@nexbid.com", "Fifth Admin", RoleName.ADMIN);
        String alice = tokenFor("watch.alice5@nexbid.com", "Alice", RoleName.BUYER);
        String bob = tokenFor("watch.bob5@nexbid.com", "Bob", RoleName.BUYER);
        String hers = approvedLot(seller, admin, "Alice's pick");
        String his = approvedLot(seller, admin, "Bob's pick");

        watch(alice, hers);
        watch(bob, his);

        myList(alice)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].auction.id").value(hers));
        myList(bob)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].auction.id").value(his));

        // EN: Bob letting go of Alice's lot changes nothing for Alice.
        // VI: Bob bỏ theo dõi lô của Alice không làm thay đổi gì với Alice.
        unwatch(bob, hers);
        myList(alice).andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void aLotYouCannotSeeCannotBeWatched() throws Exception {
        String seller = tokenFor("watch.seller6@nexbid.com", "Sixth Seller", RoleName.SELLER);
        String buyer = tokenFor("watch.buyer6@nexbid.com", "Sixth Buyer", RoleName.BUYER);
        String pending = submittedLot(seller, "Still in review");

        // EN: Same answer for a lot in review and a lot that never existed — the watch button is not a
        //     way to discover which ids are real.
        // VI: Cùng một câu trả lời cho lô đang chờ duyệt và lô chưa từng tồn tại — nút theo dõi không phải
        //     cách dò xem id nào có thật.
        watch(buyer, pending)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
        watch(buyer, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));

        assertThat(rowsFor(pending)).isZero();
    }

    @Test
    void unwatchingAnIdThatDoesNotExistAnswersExactlyLikeARealOne() throws Exception {
        String buyer = tokenFor("watch.buyer7@nexbid.com", "Seventh Buyer", RoleName.BUYER);

        unwatch(buyer, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.watching").value(false));
    }

    @Test
    void aLotThatLeavesPublicViewDropsOffTheList() throws Exception {
        String seller = tokenFor("watch.seller8@nexbid.com", "Eighth Seller", RoleName.SELLER);
        String admin = tokenFor("watch.admin8@nexbid.com", "Eighth Admin", RoleName.ADMIN);
        String buyer = tokenFor("watch.buyer8@nexbid.com", "Eighth Buyer", RoleName.BUYER);
        String kept = approvedLot(seller, admin, "Still listed");
        String pulled = approvedLot(seller, admin, "Called off");

        watch(buyer, kept);
        watch(buyer, pulled);

        jdbc.update("UPDATE auctions SET status = 'CANCELLED' WHERE id = ?::uuid", pulled);

        // EN: A card that opens onto a 404 is worse than no card.
        // VI: Một thẻ bấm vào ra 404 còn tệ hơn là không có thẻ.
        myList(buyer)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].auction.id").value(kept));

        // EN: But it can still be let go of, so the row does not linger forever.
        // VI: Nhưng vẫn bỏ theo dõi được, để dòng dữ liệu không nằm lại mãi.
        unwatch(buyer, pulled).andExpect(status().isOk());
        assertThat(rowsFor(pulled)).isZero();
    }

    @Test
    void theButtonsAndTheListNeedASignedInCaller() throws Exception {
        String seller = tokenFor("watch.seller9@nexbid.com", "Ninth Seller", RoleName.SELLER);
        String admin = tokenFor("watch.admin9@nexbid.com", "Ninth Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "Public lot");

        // EN: The lot itself is public; this asserts the carve-out in SecurityConfig holds for both verbs.
        // VI: Bản thân lô là công khai; phép thử này khẳng định phần khoét ra trong SecurityConfig có hiệu
        //     lực với cả hai động từ.
        mockMvc.perform(get("/api/auctions/" + auction)).andExpect(status().isOk());

        mockMvc.perform(post("/api/auctions/" + auction + "/watch"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
        mockMvc.perform(delete("/api/auctions/" + auction + "/watch"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/me/watchlist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void manySimultaneousTapsLeaveExactlyOneRowAndNoErrors() throws Exception {
        String seller = tokenFor("watch.seller10@nexbid.com", "Tenth Seller", RoleName.SELLER);
        String admin = tokenFor("watch.admin10@nexbid.com", "Tenth Admin", RoleName.ADMIN);
        String buyer = tokenFor("watch.buyer10@nexbid.com", "Tenth Buyer", RoleName.BUYER);
        String auction = approvedLot(seller, admin, "Mashed button");

        String me = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + buyer))
                .andReturn().getResponse().getContentAsString();
        UUID buyerId = UUID.fromString(objectMapper.readTree(me).get("data").get("id").asString());
        UUID auctionId = UUID.fromString(auction);

        // EN: A check-then-insert would let several through and the unique key would throw on the rest.
        //     ON CONFLICT DO NOTHING makes the whole thing one statement: one row, no failures.
        // VI: Kiểu "kiểm rồi mới thêm" sẽ để vài lượt lọt qua và khoá duy nhất ném lỗi với phần còn lại.
        //     ON CONFLICT DO NOTHING biến tất cả thành một câu lệnh: một dòng, không lỗi nào.
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(20);

        try (ExecutorService pool = Executors.newFixedThreadPool(20)) {
            for (int i = 0; i < 20; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        watchlist.watch(buyerId, auctionId);
                    } catch (Throwable ex) {
                        failures.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(failures).isEmpty();
        assertThat(rowsFor(auction)).isEqualTo(1);
    }
}
