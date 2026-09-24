package com.nexbid.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Auto bids (guide §31, spec §14): the spec's own example step by step, its four rules, and what
 *     happens when two auto bids meet.
 * VI: Auto bid (guide §31, spec §14): chính ví dụ của spec từng bước một, bốn luật của nó, và chuyện gì
 *     xảy ra khi hai auto bid gặp nhau.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class AutoBidApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BidService bids;

    @Autowired
    private AutoBidService autoBids;

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

    private UUID idOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("data").get("id").asString());
    }

    /** EN: Opening price 10m, increment 0.5m — the spec's numbers. / VI: Giá mở 10 triệu, bước 0,5 triệu — đúng số của spec. */
    private String lot(String seller, String admin, String name, boolean open) throws Exception {
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

        if (open) {
            jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' "
                    + "WHERE id = ?::uuid", auctionId);
        }
        return auctionId;
    }

    private ResultActions setAutoBid(String token, String auctionId, String max) throws Exception {
        return mockMvc.perform(post("/api/auctions/" + auctionId + "/auto-bid")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxAmount\":" + max + "}"));
    }

    private ResultActions bid(String token, String auctionId, String amount) throws Exception {
        return mockMvc.perform(post("/api/auctions/" + auctionId + "/bids")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    /** EN: (bidder, amount) in the order they were accepted. / VI: (người trả, số tiền) theo thứ tự được nhận. */
    private List<String> ledger(String auctionId, Map<UUID, String> names) {
        return jdbc.query("SELECT bidder_id, amount FROM bids WHERE auction_id = ?::uuid ORDER BY amount",
                (row, i) -> names.get(UUID.fromString(row.getString("bidder_id"))) + "@"
                        + row.getBigDecimal("amount").stripTrailingZeros().toPlainString(),
                auctionId);
    }

    private String price(String auctionId) {
        return jdbc.queryForObject("SELECT current_price FROM auctions WHERE id = ?::uuid",
                BigDecimal.class, auctionId).stripTrailingZeros().toPlainString();
    }

    @Test
    void theSpecsOwnExampleStepByStep() throws Exception {
        String seller = tokenFor("ab.seller1@nexbid.com", "Auto Seller", RoleName.SELLER);
        String admin = tokenFor("ab.admin1@nexbid.com", "Auto Admin", RoleName.ADMIN);
        String a = tokenFor("ab.a1@nexbid.com", "Buyer A", RoleName.BUYER);
        String b = tokenFor("ab.b1@nexbid.com", "Buyer B", RoleName.BUYER);
        String auction = lot(seller, admin, "Spec example", true);
        Map<UUID, String> names = Map.of(idOf(a), "A", idOf(b), "B");

        // EN: A sets a 20m ceiling and, with nobody leading, opens at 10m straight away.
        // VI: A đặt trần 20 triệu và, vì chưa ai dẫn, mở giá 10 triệu ngay lập tức.
        setAutoBid(a, auction, "20000000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.maxAmount").value(20000000))
                .andExpect(jsonPath("$.data.currentPrice").value(10000000))
                .andExpect(jsonPath("$.data.leading").value(true));

        // EN: Spec §14: "User B bid 11,000,000 → system bids for A 11,500,000".
        // VI: Spec §14: "User B bid 11,000,000 → hệ thống tự bid cho A 11,500,000".
        bid(b, auction, "11000000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.bid.amount").value(11000000))
                .andExpect(jsonPath("$.data.currentPrice").value(11500000))
                .andExpect(jsonPath("$.data.minimumNextBid").value(12000000))
                .andExpect(jsonPath("$.data.leading").value(false));

        // EN: "User B bid 15,000,000 → System 15,500,000".
        bid(b, auction, "15000000")
                .andExpect(jsonPath("$.data.currentPrice").value(15500000))
                .andExpect(jsonPath("$.data.leading").value(false));

        // EN: "...until it passes A's max": 20.5m is beyond 20m, so B's 20m stands.
        // VI: "...cho tới khi vượt Max của A": 20,5 triệu đã quá 20 triệu, nên 20 triệu của B đứng vững.
        bid(b, auction, "20000000")
                .andExpect(jsonPath("$.data.currentPrice").value(20000000))
                .andExpect(jsonPath("$.data.leading").value(true));

        assertThat(ledger(auction, names)).containsExactly(
                "A@10000000", "B@11000000", "A@11500000", "B@15000000", "A@15500000", "B@20000000");

        // EN: B is told each time A's auto bid overtakes. / VI: B được báo mỗi lần auto bid của A vượt lên.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'OUTBID'",
                Integer.class, idOf(b))).isEqualTo(2));

        // EN: A is told once — by the 20m bid. B's 11m and 15m overtook A for no time at all, because A's auto
        //     bid took it straight back inside the same transaction; "you've been outbid" then would be untrue.
        // VI: A chỉ được báo một lần — bởi lượt 20 triệu. Lượt 11 và 15 triệu của B vượt A chưa được chút nào,
        //     vì auto bid của A giành lại ngay trong cùng transaction; báo "bị vượt giá" lúc đó là sai sự thật.
        UUID aId = idOf(a);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'OUTBID'",
                Integer.class, aId)).isEqualTo(1));
        await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(3)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'OUTBID'",
                Integer.class, aId) == 1);
    }

    @Test
    void theCeilingMustReachTheNextMinimum() throws Exception {
        String seller = tokenFor("ab.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("ab.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String buyer = tokenFor("ab.buyer2@nexbid.com", "Second Buyer", RoleName.BUYER);
        String rival = tokenFor("ab.rival2@nexbid.com", "Second Rival", RoleName.BUYER);
        String auction = lot(seller, admin, "Too low a ceiling", true);

        bid(rival, auction, "12000000").andExpect(status().isCreated());

        // EN: Spec §14: max ≥ minimum next bid, here 12.5m.
        // VI: Spec §14: mức tối đa ≥ mức tối thiểu kế tiếp, ở đây là 12,5 triệu.
        setAutoBid(buyer, auction, "12400000")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUTO_BID_INVALID"))
                .andExpect(jsonPath("$.message").value("The max amount must be at least 12500000.00"));
    }

    @Test
    void aSellerCannotAutoBidOnTheirOwnLot() throws Exception {
        String seller = tokenFor("ab.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String admin = tokenFor("ab.admin3@nexbid.com", "Third Admin", RoleName.ADMIN);
        String auction = lot(seller, admin, "Own lot", true);

        setAutoBid(seller, auction, "20000000")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELLER_CANNOT_BID"));
    }

    @Test
    void aLotThatHasNotOpenedTakesNoAutoBid() throws Exception {
        String seller = tokenFor("ab.seller4@nexbid.com", "Fourth Seller", RoleName.SELLER);
        String admin = tokenFor("ab.admin4@nexbid.com", "Fourth Admin", RoleName.ADMIN);
        String buyer = tokenFor("ab.buyer4@nexbid.com", "Fourth Buyer", RoleName.BUYER);
        String auction = lot(seller, admin, "Not yet", false);

        setAutoBid(buyer, auction, "20000000")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_ACTIVE"));
    }

    @Test
    void oneActiveAutoBidPerPersonPerLotWhichCanBeChangedAndSwitchedOff() throws Exception {
        String seller = tokenFor("ab.seller5@nexbid.com", "Fifth Seller", RoleName.SELLER);
        String admin = tokenFor("ab.admin5@nexbid.com", "Fifth Admin", RoleName.ADMIN);
        String buyer = tokenFor("ab.buyer5@nexbid.com", "Fifth Buyer", RoleName.BUYER);
        String auction = lot(seller, admin, "Changing mind", true);

        setAutoBid(buyer, auction, "20000000").andExpect(status().isCreated());

        setAutoBid(buyer, auction, "25000000")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTO_BID_EXISTS"));

        mockMvc.perform(put("/api/auctions/" + auction + "/auto-bid")
                        .header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxAmount\":25000000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxAmount").value(25000000));

        mockMvc.perform(delete("/api/auctions/" + auction + "/auto-bid").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(get("/api/auctions/" + auction + "/auto-bid").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUTO_BID_NOT_FOUND"));

        // EN: Switching it back on reuses the same row. / VI: Bật lại thì dùng lại chính dòng cũ.
        setAutoBid(buyer, auction, "30000000").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auto_bids WHERE auction_id = ?::uuid",
                Integer.class, auction)).isEqualTo(1);
    }

    @Test
    void theMaxIsNeverShownToAnyoneElse() throws Exception {
        String seller = tokenFor("ab.seller6@nexbid.com", "Sixth Seller", RoleName.SELLER);
        String admin = tokenFor("ab.admin6@nexbid.com", "Sixth Admin", RoleName.ADMIN);
        String owner = tokenFor("ab.owner6@nexbid.com", "Sixth Owner", RoleName.BUYER);
        String stranger = tokenFor("ab.stranger6@nexbid.com", "Sixth Stranger", RoleName.BUYER);
        String auction = lot(seller, admin, "Secret ceiling", true);

        setAutoBid(owner, auction, "19750000").andExpect(status().isCreated());
        bid(stranger, auction, "11000000").andExpect(status().isCreated());

        // EN: Spec §14 and §31 (API rules): the ceiling is the owner's secret. Every view a rival can reach.
        // VI: Spec §14 và luật API: mức trần là bí mật của chủ nhân. Kiểm mọi chỗ đối thủ có thể xem được.
        for (String path : List.of("/api/auctions/" + auction, "/api/auctions/" + auction + "/bids",
                "/api/auctions?size=50")) {
            String body = mockMvc.perform(get(path).header("Authorization", "Bearer " + stranger))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).as(path).doesNotContain("19750000");
        }

        mockMvc.perform(get("/api/auctions/" + auction + "/auto-bid").header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/auctions/" + auction + "/auto-bid").header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxAmount").value(19750000))
                .andExpect(jsonPath("$.data.leading").value(true));
    }

    @Test
    void twoAutoBidsSettleInOneGoAndTheHigherCeilingWins() throws Exception {
        String seller = tokenFor("ab.seller7@nexbid.com", "Seventh Seller", RoleName.SELLER);
        String admin = tokenFor("ab.admin7@nexbid.com", "Seventh Admin", RoleName.ADMIN);
        String a = tokenFor("ab.a7@nexbid.com", "Buyer A", RoleName.BUYER);
        String c = tokenFor("ab.c7@nexbid.com", "Buyer C", RoleName.BUYER);
        String auction = lot(seller, admin, "Proxy war", true);
        Map<UUID, String> names = Map.of(idOf(a), "A", idOf(c), "C");

        setAutoBid(a, auction, "20000000");

        // EN: C (max 15m) takes on A (max 20m). Played out it is nine steps each way; only the last two are
        //     written: C tops out at 14.5m, A answers at 15m.
        // VI: C (tối đa 15 triệu) đấu với A (tối đa 20 triệu). Chạy từng bước là chín bước mỗi bên; chỉ ghi
        //     hai bước cuối: C dừng ở 14,5 triệu, A đáp trả ở 15 triệu.
        setAutoBid(c, auction, "15000000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currentPrice").value(15000000))
                .andExpect(jsonPath("$.data.leading").value(false));

        assertThat(ledger(auction, names)).containsExactly("A@10000000", "C@14500000", "A@15000000");

        // EN: A never lost the lead, so A is never told "outbid"; C is.
        // VI: A chưa từng mất vị trí dẫn, nên không bao giờ bị báo "vượt giá"; C thì có.
        UUID aId = idOf(a);
        UUID cId = idOf(c);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'OUTBID'",
                Integer.class, cId)).isEqualTo(1));
        await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(3)).until(() -> jdbc.queryForObject(
                "SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'OUTBID'",
                Integer.class, aId) == 0);
    }

    @Test
    void onAnEqualCeilingWhoeverWasThereFirstKeepsTheLead() throws Exception {
        String seller = tokenFor("ab.seller8@nexbid.com", "Eighth Seller", RoleName.SELLER);
        String admin = tokenFor("ab.admin8@nexbid.com", "Eighth Admin", RoleName.ADMIN);
        String a = tokenFor("ab.a8@nexbid.com", "Buyer A", RoleName.BUYER);
        String c = tokenFor("ab.c8@nexbid.com", "Buyer C", RoleName.BUYER);
        String auction = lot(seller, admin, "Dead heat", true);
        Map<UUID, String> names = Map.of(idOf(a), "A", idOf(c), "C");

        setAutoBid(a, auction, "20000000");
        setAutoBid(c, auction, "20000000");

        assertThat(ledger(auction, names)).containsExactly("A@10000000", "C@19500000", "A@20000000");
        assertThat(price(auction)).isEqualTo("20000000");
    }

    @Test
    void theLeaderSettingAnAutoBidDoesNotBidAgainstThemselves() throws Exception {
        String seller = tokenFor("ab.seller9@nexbid.com", "Ninth Seller", RoleName.SELLER);
        String admin = tokenFor("ab.admin9@nexbid.com", "Ninth Admin", RoleName.ADMIN);
        String buyer = tokenFor("ab.buyer9@nexbid.com", "Ninth Buyer", RoleName.BUYER);
        String auction = lot(seller, admin, "Already winning", true);

        bid(buyer, auction, "11000000");
        setAutoBid(buyer, auction, "20000000")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.currentPrice").value(11000000))
                .andExpect(jsonPath("$.data.leading").value(true));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM bids WHERE auction_id = ?::uuid",
                Integer.class, auction)).isEqualTo(1);
    }

    @Test
    void aCancelledAutoBidNoLongerAnswers() throws Exception {
        String seller = tokenFor("ab.seller10@nexbid.com", "Tenth Seller", RoleName.SELLER);
        String admin = tokenFor("ab.admin10@nexbid.com", "Tenth Admin", RoleName.ADMIN);
        String a = tokenFor("ab.a10@nexbid.com", "Buyer A", RoleName.BUYER);
        String b = tokenFor("ab.b10@nexbid.com", "Buyer B", RoleName.BUYER);
        String auction = lot(seller, admin, "Called off", true);

        setAutoBid(a, auction, "20000000");
        mockMvc.perform(delete("/api/auctions/" + auction + "/auto-bid").header("Authorization", "Bearer " + a));

        bid(b, auction, "11000000")
                .andExpect(jsonPath("$.data.currentPrice").value(11000000))
                .andExpect(jsonPath("$.data.leading").value(true));
    }

    @Test
    void theEndpointsNeedASignedInCaller() throws Exception {
        UUID any = UUID.randomUUID();
        mockMvc.perform(post("/api/auctions/" + any + "/auto-bid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxAmount\":20000000}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auctions/" + any + "/auto-bid")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/auctions/" + any + "/auto-bid")).andExpect(status().isUnauthorized());
    }

    @Test
    void autoBidsUnderAStormOfManualBidsKeepEveryInvariant() throws Exception {
        String seller = tokenFor("ab.seller11@nexbid.com", "Storm Seller", RoleName.SELLER);
        String admin = tokenFor("ab.admin11@nexbid.com", "Storm Admin", RoleName.ADMIN);
        String auction = lot(seller, admin, "Storm lot", true);
        UUID auctionId = UUID.fromString(auction);

        List<UUID> people = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO users (id, full_name, email, password, status, created_at, updated_at)
                    VALUES (?, ?, ?, 'x', 'ACTIVE', now(), now())
                    """, id, "Storm " + i, "storm" + i + "." + id + "@nexbid.com");
            people.add(id);
        }

        // EN: Three auto bidders with different ceilings, then twelve people bidding by hand all at once.
        // VI: Ba người đặt auto bid với mức trần khác nhau, rồi mười hai người trả giá tay cùng một lúc.
        // EN: Lowest first — each must still reach the next minimum when it is set (spec §14).
        // VI: Thấp nhất trước — mỗi cái vẫn phải đạt mức tối thiểu kế tiếp lúc được đặt (spec §14).
        Map<UUID, BigDecimal> ceilings = new java.util.LinkedHashMap<>();
        ceilings.put(people.get(0), new BigDecimal("25000000"));
        ceilings.put(people.get(1), new BigDecimal("40000000"));
        ceilings.put(people.get(2), new BigDecimal("60000000"));
        ceilings.forEach((user, max) -> autoBids.create(user, auctionId, max));

        AtomicLong offer = new AtomicLong();
        List<Throwable> surprises = Collections.synchronizedList(new ArrayList<>());
        List<ErrorCode> refusals = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(12);

        try (ExecutorService pool = Executors.newFixedThreadPool(12)) {
            for (int i = 3; i < 15; i++) {
                UUID bidder = people.get(i);
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int n = 0; n < 8; n++) {
                            BigDecimal amount = BigDecimal.valueOf(20_000_000L + offer.getAndIncrement() * 700_000L);
                            try {
                                bids.place(auctionId, bidder, amount);
                            } catch (BusinessException ex) {
                                refusals.add(ex.code());
                            }
                        }
                    } catch (Throwable ex) {
                        surprises.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(surprises).isEmpty();
        assertThat(refusals).isSubsetOf(ErrorCode.BID_TOO_LOW);

        Map<String, Object> lot = jdbc.queryForMap(
                "SELECT bid_count, current_price, leading_bidder_id FROM auctions WHERE id = ?::uuid", auction);
        Map<String, Object> top = jdbc.queryForMap(
                "SELECT bidder_id, amount FROM bids WHERE auction_id = ?::uuid ORDER BY amount DESC LIMIT 1", auction);

        // EN: Counter, price and leader all agree with the bid records.
        // VI: Bộ đếm, giá và người dẫn đều khớp với các bản ghi lượt trả giá.
        assertThat(((Number) lot.get("bid_count")).intValue()).isEqualTo(jdbc.queryForObject(
                "SELECT count(*) FROM bids WHERE auction_id = ?::uuid", Integer.class, auction));
        assertThat((BigDecimal) lot.get("current_price")).isEqualByComparingTo((BigDecimal) top.get("amount"));
        assertThat(lot.get("leading_bidder_id")).isEqualTo(top.get("bidder_id"));

        // EN: And no auto bid ever spent a cent past its owner's ceiling.
        // VI: Và không auto bid nào tiêu quá mức trần của chủ nhân dù chỉ một đồng.
        ceilings.forEach((user, max) -> {
            BigDecimal highest = jdbc.queryForObject(
                    "SELECT coalesce(max(amount), 0) FROM bids WHERE auction_id = ?::uuid AND bidder_id = ?",
                    BigDecimal.class, auction, user);
            assertThat(highest).isLessThanOrEqualTo(max);
        });
    }
}
