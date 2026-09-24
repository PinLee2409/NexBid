package com.nexbid.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexbid.auction.AuctionService;
import com.nexbid.bid.BidService;
import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Notifications (guide §29): who is told, when, how often — and that each person only ever reads
 *     their own.
 * VI: Thông báo (guide §29): ai được báo, khi nào, bao nhiêu lần — và mỗi người chỉ đọc được của mình.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class NotificationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionService auctions;

    @Autowired
    private BidService bids;

    @Autowired
    private NotificationTriggers triggers;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

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

    private UUID idOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("data").get("id").asString());
    }

    /** EN: Approved and still scheduled. / VI: Đã duyệt và vẫn đang chờ tới giờ. */
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

    private String activeLot(String seller, String admin, String name) throws Exception {
        String auctionId = approvedLot(seller, admin, name);
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' "
                + "WHERE id = ?::uuid", auctionId);
        return auctionId;
    }

    private void bid(String auctionId, String token, String amount) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    private void close(String auctionId) {
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", auctionId);
        auctions.endDueAuctions(Instant.now(), 200);
    }

    /** EN: The types someone has received, oldest first. / VI: Các loại thông báo một người đã nhận, cũ nhất trước. */
    private List<String> typesFor(String token) throws Exception {
        return jdbc.queryForList(
                "SELECT type FROM notifications WHERE user_id = ? ORDER BY created_at", String.class, idOf(token));
    }

    /**
     * EN: Notices are written on another thread after commit, so a test waits for them to land.
     * VI: Thông báo được ghi trên luồng khác sau commit, nên test phải chờ chúng tới nơi.
     */
    private static void eventually(org.awaitility.core.ThrowingRunnable assertion) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(assertion);
    }

    /**
     * EN: "Nobody was told" can only be shown by waiting and still seeing nothing.
     * VI: "Không ai được báo" chỉ chứng minh được bằng cách chờ mà vẫn không thấy gì.
     */
    private void staysEmpty(String token) {
        await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(3)).until(() -> typesFor(token).isEmpty());
    }

    private void watch(String token, String auctionId) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auctionId + "/watch").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void theBidderWhoLostTheLeadIsToldAndNobodyElse() throws Exception {
        String seller = tokenFor("note.seller1@nexbid.com", "Note Seller", RoleName.SELLER);
        String admin = tokenFor("note.admin1@nexbid.com", "Note Admin", RoleName.ADMIN);
        String pin = tokenFor("note.pin1@nexbid.com", "Pinnacle Buyer", RoleName.BUYER);
        String alex = tokenFor("note.alex1@nexbid.com", "Alexander Buyer", RoleName.BUYER);
        String auction = activeLot(seller, admin, "MacBook Pro M3");

        bid(auction, pin, "10000000");
        bid(auction, alex, "10500000");

        eventually(() -> assertThat(typesFor(pin)).containsExactly("OUTBID"));
        staysEmpty(alex);
        staysEmpty(seller);

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + pin))
                .andExpect(jsonPath("$.data.notifications.items[0].type").value("OUTBID"))
                .andExpect(jsonPath("$.data.notifications.items[0].title").value("You've been outbid"))
                .andExpect(jsonPath("$.data.notifications.items[0].message")
                        .value("MacBook Pro M3: someone bid 10,500,000 VND."))
                .andExpect(jsonPath("$.data.notifications.items[0].auctionId").value(auction))
                .andExpect(jsonPath("$.data.notifications.items[0].read").value(false));
    }

    @Test
    void aFirstBidAndOutbiddingYourselfTellNobody() throws Exception {
        String seller = tokenFor("note.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("note.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String buyer = tokenFor("note.buyer2@nexbid.com", "Second Buyer", RoleName.BUYER);
        String auction = activeLot(seller, admin, "Solo lot");

        bid(auction, buyer, "10000000");
        bid(auction, buyer, "10500000");

        staysEmpty(buyer);
    }

    @Test
    void beingOutbidTwiceIsTwoNotices() throws Exception {
        String seller = tokenFor("note.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String admin = tokenFor("note.admin3@nexbid.com", "Third Admin", RoleName.ADMIN);
        String pin = tokenFor("note.pin3@nexbid.com", "Pin Three", RoleName.BUYER);
        String alex = tokenFor("note.alex3@nexbid.com", "Alex Three", RoleName.BUYER);
        String auction = activeLot(seller, admin, "Tug of war");

        bid(auction, pin, "10000000");
        bid(auction, alex, "10500000");
        bid(auction, pin, "11000000");
        bid(auction, alex, "11500000");

        eventually(() -> assertThat(typesFor(pin)).containsExactly("OUTBID", "OUTBID"));
        eventually(() -> assertThat(typesFor(alex)).containsExactly("OUTBID"));
    }

    @Test
    void theCloseTellsTheWinnerAndEachOtherBidderOnce() throws Exception {
        String seller = tokenFor("note.seller4@nexbid.com", "Fourth Seller", RoleName.SELLER);
        String admin = tokenFor("note.admin4@nexbid.com", "Fourth Admin", RoleName.ADMIN);
        String pin = tokenFor("note.pin4@nexbid.com", "Pin Four", RoleName.BUYER);
        String alex = tokenFor("note.alex4@nexbid.com", "Alex Four", RoleName.BUYER);
        String john = tokenFor("note.john4@nexbid.com", "John Four", RoleName.BUYER);
        String auction = activeLot(seller, admin, "Leica M6");

        bid(auction, pin, "10000000");
        bid(auction, alex, "10500000");
        bid(auction, pin, "11000000");
        bid(auction, john, "12000000");

        close(auction);

        eventually(() -> assertThat(typesFor(john)).containsExactly("AUCTION_WON"));
        // EN: Pin bid twice but hears "lost" once. / VI: Pin trả giá hai lần nhưng chỉ nghe "thua" một lần.
        eventually(() -> assertThat(typesFor(pin)).containsExactlyInAnyOrder("OUTBID", "OUTBID", "AUCTION_LOST"));
        eventually(() -> assertThat(typesFor(alex)).containsExactlyInAnyOrder("OUTBID", "AUCTION_LOST"));
        staysEmpty(seller);

        // EN: Dated when the lot closed, not when the background write happened to run.
        // VI: Đề ngày theo lúc lô đóng, không theo lúc việc ghi chạy nền tình cờ được thực hiện.
        assertThat(jdbc.queryForObject("""
                SELECT bool_and(n.created_at = a.end_time) FROM notifications n JOIN auctions a ON a.id = n.auction_id
                WHERE n.auction_id = ?::uuid AND n.type IN ('AUCTION_WON', 'AUCTION_LOST')
                """, Boolean.class, auction)).isTrue();

        String won = jdbc.queryForObject(
                "SELECT message FROM notifications WHERE user_id = ? AND type = 'AUCTION_WON'",
                String.class, idOf(john));
        assertThat(won).isEqualTo("Leica M6: winning bid 12,000,000 VND.");
    }

    @Test
    void aLotThatSoldNothingTellsNobody() throws Exception {
        String seller = tokenFor("note.seller5@nexbid.com", "Fifth Seller", RoleName.SELLER);
        String admin = tokenFor("note.admin5@nexbid.com", "Fifth Admin", RoleName.ADMIN);
        String auction = activeLot(seller, admin, "Quiet lot");

        close(auction);

        await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(3)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE auction_id = ?::uuid", Integer.class, auction) == 0);
    }

    @Test
    void aBidThatIsRolledBackTellsNobody() throws Exception {
        String seller = tokenFor("note.seller6@nexbid.com", "Sixth Seller", RoleName.SELLER);
        String admin = tokenFor("note.admin6@nexbid.com", "Sixth Admin", RoleName.ADMIN);
        String pin = tokenFor("note.pin6@nexbid.com", "Pin Six", RoleName.BUYER);
        String alex = tokenFor("note.alex6@nexbid.com", "Alex Six", RoleName.BUYER);
        String auction = activeLot(seller, admin, "Undone lot");

        bid(auction, pin, "10000000");
        UUID alexId = idOf(alex);

        // EN: "You've been outbid" by a bid that never happened would send pin chasing a price that is not real.
        // VI: "Bạn đã bị vượt giá" bởi một lượt không có thật sẽ khiến pin chạy theo một mức giá không tồn tại.
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            bids.place(UUID.fromString(auction), alexId, new BigDecimal("10500000"));
            tx.setRollbackOnly();
        });

        staysEmpty(pin);
    }

    @Test
    void watchersHearOnceThatALotOpensSoon() throws Exception {
        String seller = tokenFor("note.seller7@nexbid.com", "Seventh Seller", RoleName.SELLER);
        String admin = tokenFor("note.admin7@nexbid.com", "Seventh Admin", RoleName.ADMIN);
        String watcher = tokenFor("note.watcher7@nexbid.com", "Watcher Seven", RoleName.BUYER);
        String bystander = tokenFor("note.bystander7@nexbid.com", "Bystander Seven", RoleName.BUYER);
        String soon = approvedLot(seller, admin, "Opens in half an hour");
        String later = approvedLot(seller, admin, "Opens tomorrow");

        jdbc.update("UPDATE auctions SET start_time = now() + interval '1 day', "
                + "end_time = now() + interval '2 days' WHERE id = ?::uuid", later);

        watch(watcher, soon);
        watch(watcher, later);

        triggers.sendHeadsUps(Instant.now());
        // EN: The job runs every minute for the whole hour; the person hears it once.
        // VI: Job chạy mỗi phút suốt cả giờ đó; người theo dõi chỉ nghe một lần.
        triggers.sendHeadsUps(Instant.now());
        triggers.sendHeadsUps(Instant.now());

        assertThat(typesFor(watcher)).containsExactly("AUCTION_STARTING");
        assertThat(jdbc.queryForObject("SELECT auction_id::text FROM notifications WHERE user_id = ?",
                String.class, idOf(watcher))).isEqualTo(soon);
        assertThat(typesFor(bystander)).isEmpty();
    }

    @Test
    void watchersHearOnceThatALotClosesSoon() throws Exception {
        String seller = tokenFor("note.seller8@nexbid.com", "Eighth Seller", RoleName.SELLER);
        String admin = tokenFor("note.admin8@nexbid.com", "Eighth Admin", RoleName.ADMIN);
        String watcher = tokenFor("note.watcher8@nexbid.com", "Watcher Eight", RoleName.BUYER);
        String auction = activeLot(seller, admin, "Closes in half an hour");

        jdbc.update("UPDATE auctions SET end_time = now() + interval '30 minutes' WHERE id = ?::uuid", auction);
        watch(watcher, auction);

        triggers.sendHeadsUps(Instant.now());
        triggers.sendHeadsUps(Instant.now());

        assertThat(typesFor(watcher)).containsExactly("AUCTION_ENDING");
    }

    @Test
    void theInboxIsNewestFirstAndCountsWhatIsUnread() throws Exception {
        String seller = tokenFor("note.seller9@nexbid.com", "Ninth Seller", RoleName.SELLER);
        String admin = tokenFor("note.admin9@nexbid.com", "Ninth Admin", RoleName.ADMIN);
        String pin = tokenFor("note.pin9@nexbid.com", "Pin Nine", RoleName.BUYER);
        String alex = tokenFor("note.alex9@nexbid.com", "Alex Nine", RoleName.BUYER);
        String auction = activeLot(seller, admin, "Busy lot");

        bid(auction, pin, "10000000");
        bid(auction, alex, "10500000");
        bid(auction, pin, "11000000");
        bid(auction, alex, "11500000");
        bid(auction, pin, "12000000");
        bid(auction, alex, "12500000");

        eventually(() -> assertThat(typesFor(pin)).hasSize(3));

        mockMvc.perform(get("/api/notifications?size=2").header("Authorization", "Bearer " + pin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(3))
                .andExpect(jsonPath("$.data.notifications.items.length()").value(2))
                .andExpect(jsonPath("$.data.notifications.totalItems").value(3))
                .andExpect(jsonPath("$.data.notifications.items[0].message")
                        .value("Busy lot: someone bid 12,500,000 VND."));
    }

    @Test
    void readingOneAndReadingAll() throws Exception {
        String seller = tokenFor("note.seller10@nexbid.com", "Tenth Seller", RoleName.SELLER);
        String admin = tokenFor("note.admin10@nexbid.com", "Tenth Admin", RoleName.ADMIN);
        String pin = tokenFor("note.pin10@nexbid.com", "Pin Ten", RoleName.BUYER);
        String alex = tokenFor("note.alex10@nexbid.com", "Alex Ten", RoleName.BUYER);
        String auction = activeLot(seller, admin, "Reading lot");

        bid(auction, pin, "10000000");
        bid(auction, alex, "10500000");
        bid(auction, pin, "11000000");
        bid(auction, alex, "11500000");

        eventually(() -> assertThat(typesFor(pin)).hasSize(2));
        eventually(() -> assertThat(typesFor(alex)).hasSize(1));

        String first = jdbc.queryForObject(
                "SELECT id::text FROM notifications WHERE user_id = ? ORDER BY created_at LIMIT 1",
                String.class, idOf(pin));

        mockMvc.perform(patch("/api/notifications/" + first + "/read").header("Authorization", "Bearer " + pin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(first))
                .andExpect(jsonPath("$.data.read").value(true));

        // EN: Reading twice is harmless. / VI: Đọc hai lần không sao.
        mockMvc.perform(patch("/api/notifications/" + first + "/read").header("Authorization", "Bearer " + pin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + pin))
                .andExpect(jsonPath("$.data.unreadCount").value(1));

        mockMvc.perform(patch("/api/notifications/read-all").header("Authorization", "Bearer " + pin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.marked").value(1));
        mockMvc.perform(patch("/api/notifications/read-all").header("Authorization", "Bearer " + pin))
                .andExpect(jsonPath("$.data.marked").value(0));

        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + pin))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        // EN: Pin reading all did nothing to Alex's. / VI: Pin đọc hết không động gì tới của Alex.
        mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + alex))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void nobodyCanReadOrTouchSomeoneElsesNotice() throws Exception {
        String seller = tokenFor("note.seller11@nexbid.com", "Eleventh Seller", RoleName.SELLER);
        String admin = tokenFor("note.admin11@nexbid.com", "Eleventh Admin", RoleName.ADMIN);
        String pin = tokenFor("note.pin11@nexbid.com", "Pin Eleven", RoleName.BUYER);
        String alex = tokenFor("note.alex11@nexbid.com", "Alex Eleven", RoleName.BUYER);
        String auction = activeLot(seller, admin, "Private lot");

        bid(auction, pin, "10000000");
        bid(auction, alex, "10500000");

        eventually(() -> assertThat(typesFor(pin)).hasSize(1));

        String pinsNotice = jdbc.queryForObject(
                "SELECT id::text FROM notifications WHERE user_id = ?", String.class, idOf(pin));

        // EN: Same answer as a made-up id, so the endpoint cannot be used to find out which ids exist.
        // VI: Cùng câu trả lời như id bịa, nên endpoint không dùng để dò xem id nào có thật được.
        mockMvc.perform(patch("/api/notifications/" + pinsNotice + "/read").header("Authorization", "Bearer " + alex))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
        mockMvc.perform(patch("/api/notifications/" + UUID.randomUUID() + "/read")
                        .header("Authorization", "Bearer " + alex))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        assertThat(jdbc.queryForObject("SELECT is_read FROM notifications WHERE id = ?::uuid",
                Boolean.class, pinsNotice)).isFalse();

        String alexInbox = mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + alex))
                .andReturn().getResponse().getContentAsString();
        assertThat(alexInbox).doesNotContain(pinsNotice);
    }

    @Test
    void theBellNeedsASignedInCaller() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/read-all")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/notifications/" + UUID.randomUUID() + "/read"))
                .andExpect(status().isUnauthorized());
    }
}
