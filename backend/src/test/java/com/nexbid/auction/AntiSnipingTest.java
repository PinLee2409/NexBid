package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Anti-sniping (guide §30, spec §13): a bid in the last seconds pushes the close back, and the rest of
 *     the system — the response, the scheduler, the live channel — agrees on the new time.
 * VI: Chống bid phút chót (guide §30, spec §13): lượt trả giá ở những giây cuối lùi giờ đóng lại, và phần
 *     còn lại của hệ thống — response, scheduler, kênh realtime — đều thống nhất về mốc giờ mới.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class AntiSnipingTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionService auctions;

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

    /**
     * EN: An open lot with the spec's settings — 30-second window, 120-second extension — closing in
     *     {@code closesIn}.
     * VI: Một lô đang mở với cấu hình của spec — khung 30 giây, gia hạn 120 giây — đóng sau {@code closesIn}.
     */
    private String openLot(String seller, String admin, String name, boolean antiSniping, Duration closesIn)
            throws Exception {

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
                                 "startTime":"%s","endTime":"%s",
                                 "antiSnipingEnabled":%s,"antiSnipingWindowSeconds":30,"extensionSeconds":120}
                                """.formatted(productId,
                                Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)),
                                antiSniping)))
                .andReturn().getResponse().getContentAsString();
        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();

        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit")
                .header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve")
                .header("Authorization", "Bearer " + admin));

        closeIn(auctionId, closesIn);
        return auctionId;
    }

    private void closeIn(String auctionId, Duration closesIn) {
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour', "
                + "end_time = now() + make_interval(secs => ?) WHERE id = ?::uuid",
                closesIn.toMillis() / 1000.0, auctionId);
    }

    private Instant endOf(String auctionId) {
        return jdbc.queryForObject("SELECT end_time FROM auctions WHERE id = ?::uuid",
                java.sql.Timestamp.class, auctionId).toInstant();
    }

    private int extensionsOf(String auctionId) {
        return jdbc.queryForObject("SELECT extension_count FROM auctions WHERE id = ?::uuid",
                Integer.class, auctionId);
    }

    /** EN: Places a bid and returns the end time the response reports. / VI: Trả giá và trả về giờ đóng mà response báo. */
    private Instant bid(String auctionId, String token, String amount) throws Exception {
        String body = mockMvc.perform(post("/api/auctions/" + auctionId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return Instant.parse(objectMapper.readTree(body).get("data").get("endTime").asString());
    }

    @Test
    void aBidWithFifteenSecondsLeftPushesTheCloseBackByTwoMinutes() throws Exception {
        String seller = tokenFor("snipe.seller1@nexbid.com", "Snipe Seller", RoleName.SELLER);
        String admin = tokenFor("snipe.admin1@nexbid.com", "Snipe Admin", RoleName.ADMIN);
        String buyer = tokenFor("snipe.buyer1@nexbid.com", "Snipe Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Last seconds", true, Duration.ofSeconds(15));
        Instant originalEnd = endOf(auction);

        Instant reported = bid(auction, buyer, "10000000");

        // EN: Guide §30's own test. The old close plus 120 s, and the bidder hears it in the same response.
        // VI: Chính phép thử của guide §30. Giờ đóng cũ cộng 120 giây, và người trả giá biết ngay trong response.
        assertThat(endOf(auction)).isEqualTo(originalEnd.plusSeconds(120));
        assertThat(reported).isEqualTo(originalEnd.plusSeconds(120));
        assertThat(extensionsOf(auction)).isEqualTo(1);
    }

    @Test
    void aBidWithTimeToSpareChangesNothing() throws Exception {
        String seller = tokenFor("snipe.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("snipe.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String buyer = tokenFor("snipe.buyer2@nexbid.com", "Second Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Plenty of time", true, Duration.ofSeconds(45));
        Instant originalEnd = endOf(auction);

        bid(auction, buyer, "10000000");

        assertThat(endOf(auction)).isEqualTo(originalEnd);
        assertThat(extensionsOf(auction)).isZero();
    }

    @Test
    void aSellerWhoTurnedItOffGetsNoExtension() throws Exception {
        String seller = tokenFor("snipe.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String admin = tokenFor("snipe.admin3@nexbid.com", "Third Admin", RoleName.ADMIN);
        String buyer = tokenFor("snipe.buyer3@nexbid.com", "Third Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Hard close", false, Duration.ofSeconds(15));
        Instant originalEnd = endOf(auction);

        bid(auction, buyer, "10000000");

        assertThat(endOf(auction)).isEqualTo(originalEnd);
    }

    @Test
    void everyLastMinuteBidExtendsAgain() throws Exception {
        String seller = tokenFor("snipe.seller4@nexbid.com", "Fourth Seller", RoleName.SELLER);
        String admin = tokenFor("snipe.admin4@nexbid.com", "Fourth Admin", RoleName.ADMIN);
        String pin = tokenFor("snipe.pin4@nexbid.com", "Pin Four", RoleName.BUYER);
        String alex = tokenFor("snipe.alex4@nexbid.com", "Alex Four", RoleName.BUYER);
        String auction = openLot(seller, admin, "Bidding war", true, Duration.ofSeconds(15));

        bid(auction, pin, "10000000");

        // EN: The clock runs down again (wound forward here), and the next last-second bid extends again.
        //     No cap: spec §13 sets none, and the extension is exactly what keeps the war fair.
        // VI: Đồng hồ lại chạy tới sát giờ (ở đây vặn tới cho nhanh), và lượt trả giá phút chót tiếp theo lại
        //     gia hạn. Không có giới hạn: spec §13 không đặt, và chính việc gia hạn giữ cho cuộc đua công bằng.
        closeIn(auction, Duration.ofSeconds(10));
        Instant beforeSecond = endOf(auction);

        bid(auction, alex, "10500000");

        assertThat(endOf(auction)).isEqualTo(beforeSecond.plusSeconds(120));
        assertThat(extensionsOf(auction)).isEqualTo(2);
    }

    @Test
    void theSchedulerDoesNotCloseALotAtItsOriginalTimeOnceItWasExtended() throws Exception {
        String seller = tokenFor("snipe.seller5@nexbid.com", "Fifth Seller", RoleName.SELLER);
        String admin = tokenFor("snipe.admin5@nexbid.com", "Fifth Admin", RoleName.ADMIN);
        String buyer = tokenFor("snipe.buyer5@nexbid.com", "Fifth Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Moved deadline", true, Duration.ofSeconds(15));
        Instant originalEnd = endOf(auction);

        bid(auction, buyer, "10000000");

        auctions.endDueAuctions(originalEnd.plusSeconds(1), 200);
        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, auction))
                .isEqualTo("ACTIVE");

        auctions.endDueAuctions(originalEnd.plusSeconds(121), 200);
        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, auction))
                .isEqualTo("ENDED");
    }

    @Test
    void aClosingThatWasAlreadyWaitingStillHonoursTheExtension() throws Exception {
        String seller = tokenFor("snipe.seller6@nexbid.com", "Sixth Seller", RoleName.SELLER);
        String admin = tokenFor("snipe.admin6@nexbid.com", "Sixth Admin", RoleName.ADMIN);
        String auction = openLot(seller, admin, "Photo finish", true, Duration.ofMillis(400));

        // EN: Plays a last-second bid that is committing: it holds the row and moves the close back by 120 s.
        // VI: Đóng vai một lượt trả giá giây chót đang commit: nó giữ dòng và lùi giờ đóng thêm 120 giây.
        CountDownLatch holding = new CountDownLatch(1);

        try (ExecutorService inFlight = Executors.newSingleThreadExecutor()) {
            var committed = inFlight.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM auctions WHERE id = ?::uuid FOR UPDATE", UUID.class, auction);
                jdbc.update("UPDATE auctions SET end_time = end_time + interval '120 seconds', "
                        + "extension_count = extension_count + 1, version = version + 1 WHERE id = ?::uuid", auction);
                holding.countDown();
                sleepQuietly(Duration.ofMillis(1200));
            }));

            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();
            // EN: By now the original close has passed, so the closer finds the lot due and waits on the lock.
            // VI: Lúc này giờ đóng ban đầu đã qua, nên bên đóng phiên thấy lô tới hạn và chờ ở khoá.
            Thread.sleep(600);

            // EN: When the lock frees, the database re-checks "end_time <= now" against the extended row and
            //     drops it. The bidder who sniped is not robbed of the two minutes they just earned.
            // VI: Khi khoá mở, database kiểm lại "end_time <= now" trên dòng đã gia hạn và bỏ nó ra. Người vừa
            //     trả giá phút chót không bị cướp mất hai phút họ vừa giành được.
            assertThat(auctions.endDueAuctions(Instant.now(), 200)).isZero();

            committed.get(10, TimeUnit.SECONDS);
        }

        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, auction))
                .isEqualTo("ACTIVE");
    }

    @Test
    void everyoneWatchingHearsTheNewClosingTime() throws Exception {
        String seller = tokenFor("snipe.seller7@nexbid.com", "Seventh Seller", RoleName.SELLER);
        String admin = tokenFor("snipe.admin7@nexbid.com", "Seventh Admin", RoleName.ADMIN);
        String pin = tokenFor("snipe.pin7@nexbid.com", "Pin Seven", RoleName.BUYER);
        String alex = tokenFor("snipe.alex7@nexbid.com", "Alex Seven", RoleName.BUYER);
        String auction = openLot(seller, admin, "Watched finish", true, Duration.ofSeconds(45));

        BlockingQueue<Map<String, Object>> inbox = watch(auction);

        // EN: Outside the window: a price update, no extension. / VI: Ngoài khung: chỉ cập nhật giá, không gia hạn.
        bid(auction, pin, "10000000");
        List<String> first = drain(inbox);
        assertThat(first).containsExactly("BID_PLACED");

        closeIn(auction, Duration.ofSeconds(15));
        Instant originalEnd = endOf(auction);
        bid(auction, alex, "10500000");

        // EN: Spec §13 — without this, every open page counts down to a close that no longer exists.
        // VI: Spec §13 — thiếu bản tin này, mọi trang đang mở vẫn đếm ngược tới một giờ đóng không còn tồn tại.
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            messages.add(inbox.poll(10, TimeUnit.SECONDS));
        }
        Map<String, Object> extended = messages.stream()
                .filter(m -> m != null && "AUCTION_EXTENDED".equals(m.get("type")))
                .findFirst().orElseThrow();

        assertThat(extended).containsEntry("auctionId", auction);
        assertThat(Instant.parse((String) extended.get("endTime"))).isEqualTo(originalEnd.plusSeconds(120));
        assertThat(extended.get("serverTime")).isNotNull();
        assertThat(messages).extracting(m -> m.get("type")).containsExactlyInAnyOrder("BID_PLACED", "AUCTION_EXTENDED");
    }

    private List<String> drain(BlockingQueue<Map<String, Object>> inbox) throws InterruptedException {
        List<String> types = new ArrayList<>();
        Map<String, Object> message;
        while ((message = inbox.poll(1500, TimeUnit.MILLISECONDS)) != null) {
            types.add((String) message.get("type"));
        }
        return types;
    }

    private static void sleepQuietly(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private BlockingQueue<Map<String, Object>> watch(String auctionId) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());

        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin("http://localhost:3000");

        StompSession session = client.connectAsync(
                        "ws://localhost:" + port + "/ws", handshake, new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);

        BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();

        session.subscribe("/topic/auctions/" + auctionId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                inbox.add((Map<String, Object>) payload);
            }
        });

        // EN: Give the SUBSCRIBE frame time to reach the broker. / VI: Cho frame SUBSCRIBE kịp tới broker.
        Thread.sleep(300);

        return inbox;
    }
}
