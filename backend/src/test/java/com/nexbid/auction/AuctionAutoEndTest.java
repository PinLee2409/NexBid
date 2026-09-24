package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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

import com.nexbid.bid.BidService;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Closing lots on time (guide §26). The scheduler is off and the rule is called directly, so each
 *     test decides when "now" is instead of racing a background thread.
 * VI: Đóng lô đúng giờ (guide §26). Tắt scheduler và gọi thẳng luật, để mỗi test tự quyết "bây giờ" là
 *     lúc nào thay vì chạy đua với một luồng chạy nền.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class AuctionAutoEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionService auctions;

    @Autowired
    private BidService bids;

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

    /** EN: Approved, open, and taking bids. / VI: Đã duyệt, đang mở và nhận trả giá. */
    private String activeLot(String seller, String admin, String name) throws Exception {
        String auctionId = submittedLot(seller, name);

        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve")
                .header("Authorization", "Bearer " + admin));

        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' "
                + "WHERE id = ?::uuid", auctionId);

        return auctionId;
    }

    /** EN: Winds the end time back so the lot is due to close. / VI: Vặn lùi giờ đóng để lô tới hạn đóng. */
    private void makeDue(String auctionId) {
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", auctionId);
    }

    private String statusOf(String auctionId) {
        return jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, auctionId);
    }

    private void bid(String auctionId, String token, String amount, int expected) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().is(expected));
    }

    private void drain() {
        while (auctions.endDueAuctions(Instant.now(), 500) > 0) {
            // EN: Keep going until nothing is due. / VI: Chạy tiếp tới khi không còn gì tới hạn.
        }
    }

    @Test
    void aLotWhoseClockRanOutIsClosedAndTheResultStaysAsTheBiddingLeftIt() throws Exception {
        String seller = tokenFor("end.seller1@nexbid.com", "End Seller", RoleName.SELLER);
        String admin = tokenFor("end.admin1@nexbid.com", "End Admin", RoleName.ADMIN);
        String buyer = tokenFor("end.buyer1@nexbid.com", "End Buyer", RoleName.BUYER);
        String auction = activeLot(seller, admin, "Closing lot");

        bid(auction, buyer, "10000000", 201);
        bid(auction, buyer, "10500000", 201);

        makeDue(auction);
        auctions.endDueAuctions(Instant.now(), 200);

        assertThat(statusOf(auction)).isEqualTo("ENDED");

        // EN: Closing is a status change and nothing else. The number the last bidder saw is the result.
        // VI: Đóng phiên chỉ là đổi trạng thái, không gì khác. Con số người trả giá cuối nhìn thấy chính là kết quả.
        mockMvc.perform(get("/api/auctions/" + auction))
                .andExpect(jsonPath("$.data.auction.status").value("ENDED"))
                .andExpect(jsonPath("$.data.auction.currentPrice").value(10500000))
                .andExpect(jsonPath("$.data.auction.bidCount").value(2))
                .andExpect(jsonPath("$.data.openForBidding").value(false));

        bid(auction, buyer, "11000000", 409);

        // EN: An ended lot is still a public record — its page and its history stay readable.
        // VI: Lô đã đóng vẫn là hồ sơ công khai — trang và lịch sử của nó vẫn đọc được.
        mockMvc.perform(get("/api/auctions/" + auction + "/bids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2));
    }

    @Test
    void aLotWithTimeLeftIsLeftOpen() throws Exception {
        String seller = tokenFor("end.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("end.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String auction = activeLot(seller, admin, "Still running");

        auctions.endDueAuctions(Instant.now(), 200);

        assertThat(statusOf(auction)).isEqualTo("ACTIVE");
    }

    @Test
    void aLotWhoseWholeWindowPassedUnopenedIsClosedWithoutEverOpening() throws Exception {
        String seller = tokenFor("end.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String admin = tokenFor("end.admin3@nexbid.com", "Third Admin", RoleName.ADMIN);
        String auction = submittedLot(seller, "Missed entirely");
        mockMvc.perform(post("/api/admin/auctions/" + auction + "/approve")
                .header("Authorization", "Bearer " + admin));

        jdbc.update("UPDATE auctions SET start_time = now() - interval '5 hours', "
                + "end_time = now() - interval '4 hours' WHERE id = ?::uuid", auction);

        BlockingQueue<Map<String, Object>> inbox = watch(auction);

        // EN: One scheduler tick, in its real order. The lot the auto-start step deliberately skipped —
        //     found in real dev data — must land in ENDED, and must never pass through ACTIVE on the way.
        // VI: Một nhịp scheduler, đúng thứ tự thật. Lô mà bước tự mở cố ý bỏ qua — phát hiện từ dữ liệu dev
        //     thật — phải về ENDED, và không bao giờ đi ngang qua ACTIVE.
        Instant now = Instant.now();
        auctions.endDueAuctions(now, 200);
        auctions.startDueAuctions(now, 200);

        assertThat(statusOf(auction)).isEqualTo("ENDED");

        Map<String, Object> message = inbox.poll(10, TimeUnit.SECONDS);
        assertThat(message).containsEntry("type", "AUCTION_ENDED");
        assertThat(inbox.poll(1500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void lotsThatWereNeverApprovedAreNotTouched() throws Exception {
        String seller = tokenFor("end.seller4@nexbid.com", "Fourth Seller", RoleName.SELLER);
        String pending = submittedLot(seller, "Waiting for review");
        jdbc.update("UPDATE auctions SET start_time = now() - interval '2 hours', "
                + "end_time = now() - interval '1 hour' WHERE id = ?::uuid", pending);

        auctions.endDueAuctions(Instant.now(), 200);

        // EN: The clock closes auctions; it does not decide what happens to lots nobody approved.
        // VI: Đồng hồ đóng phiên đấu giá; nó không quyết định số phận của những lô chưa ai duyệt.
        assertThat(statusOf(pending)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void runningAgainChangesNothing() throws Exception {
        String seller = tokenFor("end.seller5@nexbid.com", "Fifth Seller", RoleName.SELLER);
        String admin = tokenFor("end.admin5@nexbid.com", "Fifth Admin", RoleName.ADMIN);

        drain();

        String auction = activeLot(seller, admin, "Closed once");
        makeDue(auction);

        assertThat(auctions.endDueAuctions(Instant.now(), 200)).isEqualTo(1);
        assertThat(auctions.endDueAuctions(Instant.now(), 200)).isZero();
        assertThat(statusOf(auction)).isEqualTo("ENDED");
    }

    @Test
    void aLargeBacklogIsWorkedThroughInBatches() throws Exception {
        String seller = tokenFor("end.seller6@nexbid.com", "Sixth Seller", RoleName.SELLER);
        String admin = tokenFor("end.admin6@nexbid.com", "Sixth Admin", RoleName.ADMIN);

        drain();

        List<String> lots = List.of(
                activeLot(seller, admin, "Batch one"),
                activeLot(seller, admin, "Batch two"),
                activeLot(seller, admin, "Batch three"));
        lots.forEach(this::makeDue);

        assertThat(auctions.endDueAuctions(Instant.now(), 2)).isEqualTo(2);
        assertThat(auctions.endDueAuctions(Instant.now(), 2)).isEqualTo(1);
        assertThat(auctions.endDueAuctions(Instant.now(), 2)).isZero();

        lots.forEach(lot -> assertThat(statusOf(lot)).isEqualTo("ENDED"));
    }

    @Test
    void everyoneWatchingTheLotIsToldItClosedExactlyOnce() throws Exception {
        String seller = tokenFor("end.seller7@nexbid.com", "Seventh Seller", RoleName.SELLER);
        String admin = tokenFor("end.admin7@nexbid.com", "Seventh Admin", RoleName.ADMIN);
        String auction = activeLot(seller, admin, "Announced close");

        BlockingQueue<Map<String, Object>> inbox = watch(auction);

        makeDue(auction);
        auctions.endDueAuctions(Instant.now(), 200);

        Map<String, Object> message = inbox.poll(10, TimeUnit.SECONDS);

        // EN: Without this the page's countdown hits zero and the bid button stays live until a refresh.
        // VI: Thiếu bản tin này, đồng hồ trên trang về 0 mà nút trả giá vẫn bấm được cho tới khi tải lại.
        assertThat(message).isNotNull();
        assertThat(message).containsEntry("type", "AUCTION_ENDED");
        assertThat(message).containsEntry("auctionId", auction);
        assertThat(message).containsEntry("status", "ENDED");
        assertThat(message.get("serverTime")).isNotNull();

        auctions.endDueAuctions(Instant.now(), 200);
        assertThat(inbox.poll(1500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void biddersRacingTheClosingBellNeverLeaveTheLotInAnInconsistentState() throws Exception {
        String seller = tokenFor("end.seller8@nexbid.com", "Race Seller", RoleName.SELLER);
        String admin = tokenFor("end.admin8@nexbid.com", "Race Admin", RoleName.ADMIN);
        String auction = activeLot(seller, admin, "Photo finish");
        UUID auctionId = UUID.fromString(auction);

        // EN: Closes 1.5 s from now, with bidders hammering it right through the deadline.
        // VI: Đóng sau 1,5 giây, với người trả giá dồn dập ngay qua mốc hạn chót.
        jdbc.update("UPDATE auctions SET end_time = now() + interval '1500 milliseconds' WHERE id = ?::uuid",
                auction);

        List<UUID> bidders = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO users (id, full_name, email, password, status, created_at, updated_at)
                    VALUES (?, ?, ?, 'x', 'ACTIVE', now(), now())
                    """, id, "Racer " + i, "end.racer" + i + "." + id + "@nexbid.com");
            bidders.add(id);
        }

        // EN: Every offer is higher than the one before it in issue order, so arrival order alone decides
        //     which are too low — a genuine mix of accepted and refused bids, not a scripted one.
        // VI: Mỗi lời trả giá cao hơn lời trước theo thứ tự phát ra, nên chỉ thứ tự tới nơi quyết định lượt
        //     nào quá thấp — một hỗn hợp thật giữa lượt được nhận và bị từ chối, không phải kịch bản sẵn.
        AtomicLong nextOffer = new AtomicLong(0);
        AtomicInteger accepted = new AtomicInteger();
        List<ErrorCode> refusals = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> surprises = Collections.synchronizedList(new ArrayList<>());
        Instant stopAt = Instant.now().plusSeconds(3);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(bidders.size() + 1);

        try (ExecutorService pool = Executors.newFixedThreadPool(bidders.size() + 1)) {
            for (UUID bidder : bidders) {
                pool.submit(() -> {
                    try {
                        start.await();
                        while (Instant.now().isBefore(stopAt)) {
                            BigDecimal amount = BigDecimal.valueOf(
                                    10_000_000L + nextOffer.getAndIncrement() * 1_000_000L);
                            try {
                                bids.place(auctionId, bidder, amount);
                                accepted.incrementAndGet();
                            } catch (BusinessException ex) {
                                refusals.add(ex.code());
                                if (ex.code() == ErrorCode.AUCTION_ALREADY_ENDED) {
                                    return null;
                                }
                            }
                        }
                    } catch (Throwable ex) {
                        surprises.add(ex);
                    } finally {
                        done.countDown();
                    }
                    return null;
                });
            }

            // EN: The closing bell, ringing every 20 ms like an impatient scheduler.
            // VI: Tiếng búa đóng phiên, gõ mỗi 20 ms như một scheduler sốt ruột.
            pool.submit(() -> {
                try {
                    start.await();
                    while (Instant.now().isBefore(stopAt) && !"ENDED".equals(statusOf(auction))) {
                        auctions.endDueAuctions(Instant.now(), 200);
                        Thread.sleep(20);
                    }
                } catch (Throwable ex) {
                    surprises.add(ex);
                } finally {
                    done.countDown();
                }
                return null;
            });

            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        // EN: No deadlocks, no optimistic-lock failures, not even a BID_CONFLICT — under the lock every
        //     loser reads the moved price or the closed lot, so only these two refusals are possible.
        // VI: Không deadlock, không lỗi khoá lạc quan, thậm chí không có BID_CONFLICT — dưới khoá, bên thua
        //     luôn đọc được giá đã đổi hoặc lô đã đóng, nên chỉ có thể có hai kiểu từ chối này.
        assertThat(surprises).isEmpty();
        assertThat(refusals).isSubsetOf(ErrorCode.BID_TOO_LOW, ErrorCode.AUCTION_ALREADY_ENDED);

        // EN: The race really crossed the deadline — some bids got in, some arrived too late.
        // VI: Cuộc đua thật sự vượt qua mốc hạn — có lượt vào kịp, có lượt tới quá muộn.
        assertThat(accepted.get()).isPositive();
        assertThat(refusals).contains(ErrorCode.AUCTION_ALREADY_ENDED);

        assertThat(statusOf(auction)).isEqualTo("ENDED");

        Map<String, Object> lot = jdbc.queryForMap(
                "SELECT bid_count, current_price, end_time FROM auctions WHERE id = ?::uuid", auction);
        Map<String, Object> record = jdbc.queryForMap(
                "SELECT count(*) AS n, max(amount) AS top, max(created_at) AS latest "
                        + "FROM bids WHERE auction_id = ?::uuid", auction);

        // EN: The lot's counters and the bid records tell the same story.
        // VI: Bộ đếm của lô và các bản ghi trả giá kể cùng một câu chuyện.
        assertThat(((Number) lot.get("bid_count")).intValue()).isEqualTo(accepted.get());
        assertThat(((Number) record.get("n")).intValue()).isEqualTo(accepted.get());
        assertThat((BigDecimal) lot.get("current_price")).isEqualByComparingTo((BigDecimal) record.get("top"));

        // EN: Sixteen bidders racing the bell, and the winner is still exactly the author of the top bid.
        // VI: Mười sáu người đua với tiếng búa, mà người thắng vẫn đúng là chủ nhân lượt trả giá cao nhất.
        String topBidder = jdbc.queryForObject(
                "SELECT bidder_id::text FROM bids WHERE auction_id = ?::uuid ORDER BY amount DESC LIMIT 1",
                String.class, auction);
        assertThat(jdbc.queryForObject("SELECT winner_id::text FROM auctions WHERE id = ?::uuid",
                String.class, auction)).isEqualTo(topBidder);

        // EN: And no accepted bid is stamped at or after the close.
        // VI: Và không lượt trả giá được nhận nào mang dấu thời gian bằng hoặc sau giờ đóng.
        assertThat(((java.sql.Timestamp) record.get("latest")).toInstant())
                .isBefore(((java.sql.Timestamp) lot.get("end_time")).toInstant());
    }

    @Test
    void theClosingBellWaitsForABidThatIsStillCommitting() throws Exception {
        String seller = tokenFor("end.seller9@nexbid.com", "Ninth Seller", RoleName.SELLER);
        String admin = tokenFor("end.admin9@nexbid.com", "Ninth Admin", RoleName.ADMIN);
        String auction = activeLot(seller, admin, "Last second lot");
        makeDue(auction);

        // EN: Plays the part of a bid that passed its clock check a moment before the deadline and is
        //     now committing: it holds the row lock and moves the price and version, as a real one does.
        // VI: Đóng vai một lượt trả giá đã qua bước kiểm giờ ngay trước hạn chót và đang commit: nó giữ khoá
        //     dòng và đẩy giá cùng version, y như lượt thật.
        CountDownLatch holding = new CountDownLatch(1);

        try (ExecutorService inFlight = Executors.newSingleThreadExecutor()) {
            var committed = inFlight.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM auctions WHERE id = ?::uuid FOR UPDATE", UUID.class, auction);
                jdbc.update("UPDATE auctions SET current_price = 12345000, bid_count = bid_count + 1, "
                        + "version = version + 1 WHERE id = ?::uuid", auction);
                holding.countDown();
                sleepQuietly(Duration.ofMillis(800));
            }));

            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();

            // EN: With the lock, closing waits for that commit and then reads the lot as the bid left it.
            //     Without it, closing reads the old row, and its write is refused by the version check —
            //     the scheduler fails instead of closing.
            // VI: Có khoá, việc đóng phiên chờ lượt commit đó rồi đọc lô đúng như lượt trả giá để lại. Không
            //     có khoá, việc đóng đọc dòng cũ, và lệnh ghi bị kiểm tra version từ chối — scheduler hỏng
            //     thay vì đóng được phiên.
            assertThat(auctions.endDueAuctions(Instant.now(), 200)).isEqualTo(1);

            committed.get(10, TimeUnit.SECONDS);
        }

        assertThat(statusOf(auction)).isEqualTo("ENDED");
        assertThat(jdbc.queryForObject(
                "SELECT current_price FROM auctions WHERE id = ?::uuid", BigDecimal.class, auction))
                .isEqualByComparingTo("12345000");
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
