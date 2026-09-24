package com.nexbid.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
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
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Realtime bidding (guide §23). The guide's test is "open two browsers, bid in one, the other must see
 *     it" — two real STOMP clients on the real port are that test, minus the browsers.
 * VI: Trả giá realtime (guide §23). Phép thử của guide là "mở hai trình duyệt, trả giá ở một bên, bên kia
 *     phải thấy ngay" — hai STOMP client thật trên cổng thật chính là phép thử đó, chỉ bỏ phần trình duyệt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class RealtimeBidTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private BidService bids;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * EN: A subscriber to one lot's channel, with an inbox the test can wait on.
     * VI: Một người đăng ký kênh của một lô, kèm hộp thư mà test có thể chờ trên đó.
     */
    private record Watcher(StompSession session, BlockingQueue<Map<String, Object>> inbox) {

        @SuppressWarnings("unchecked")
        Map<String, Object> awaitMessage() throws InterruptedException {
            return inbox.poll(10, TimeUnit.SECONDS);
        }

        boolean heardNothingWithin(Duration window) throws InterruptedException {
            return inbox.poll(window.toMillis(), TimeUnit.MILLISECONDS) == null;
        }
    }

    private Watcher watch(String auctionId) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());

        // EN: The origin the handshake will accept — the same one a browser would send.
        // VI: Origin mà bước bắt tay chấp nhận — đúng cái mà trình duyệt sẽ gửi.
        var handshakeHeaders = new org.springframework.web.socket.WebSocketHttpHeaders();
        handshakeHeaders.setOrigin("http://localhost:3000");

        StompSession session = client.connectAsync(
                        "ws://localhost:" + port + "/ws",
                        handshakeHeaders,
                        new StompSessionHandlerAdapter() {})
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

        return new Watcher(session, inbox);
    }

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

    private String openLot(String seller, String admin, String name) throws Exception {
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

        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 minute' "
                + "WHERE id = ?::uuid", auctionId);

        return auctionId;
    }

    private void bid(String auctionId, String token, String amount, int expectedStatus) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void everyoneWatchingTheLotSeesTheBid() throws Exception {
        String seller = tokenFor("rt.seller1@nexbid.com", "Realtime Seller", RoleName.SELLER);
        String admin = tokenFor("rt.admin1@nexbid.com", "Realtime Admin", RoleName.ADMIN);
        String bidder = tokenFor("rt.pin1@nexbid.com", "Pinnacle Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Watched lot");

        // EN: Two browsers, as the guide puts it.
        // VI: Hai trình duyệt, đúng như guide nói.
        Watcher alice = watch(auction);
        Watcher bob = watch(auction);

        bid(auction, bidder, "10000000", 201);

        for (Watcher watcher : new Watcher[] { alice, bob }) {
            Map<String, Object> message = watcher.awaitMessage();

            assertThat(message).isNotNull();
            assertThat(message).containsEntry("type", "BID_PLACED");
            assertThat(message).containsEntry("auctionId", auction);
            assertThat(message).containsEntry("bidCount", 1);
            assertThat(message).containsEntry("bidder", "pin***");
            assertThat(new java.math.BigDecimal(message.get("currentPrice").toString()))
                    .isEqualByComparingTo("10000000");
            // EN: The new floor travels with the news, so no browser has to work out a price itself.
            // VI: Mức sàn mới đi kèm bản tin, để không trình duyệt nào phải tự tính ra một mức giá.
            assertThat(new java.math.BigDecimal(message.get("minimumNextBid").toString()))
                    .isEqualByComparingTo("10500000");
            assertThat(message.get("createdAt")).isNotNull();
        }

        alice.session().disconnect();
        bob.session().disconnect();
    }

    @Test
    void theMessageNeverNamesTheBidder() throws Exception {
        String seller = tokenFor("rt.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("rt.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String bidder = tokenFor("rt.buyer2@nexbid.com", "Pinnacle Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Private lot");

        Watcher watcher = watch(auction);
        bid(auction, bidder, "10000000", 201);

        Map<String, Object> message = watcher.awaitMessage();

        // EN: The socket is a broadcast to strangers, so it must be no more revealing than the public
        //     history — and carry no id that would undo the mask.
        // VI: Socket là bản tin phát cho người lạ, nên không được lộ hơn lịch sử công khai — và không mang
        //     theo id nào có thể phá bỏ lớp che tên.
        assertThat(message.toString()).doesNotContain("Pinnacle");
        assertThat(message).doesNotContainKeys("bidderId", "bidId", "email");

        watcher.session().disconnect();
    }

    @Test
    void aRefusedBidIsBroadcastToNobody() throws Exception {
        String seller = tokenFor("rt.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String admin = tokenFor("rt.admin3@nexbid.com", "Third Admin", RoleName.ADMIN);
        String bidder = tokenFor("rt.buyer3@nexbid.com", "Third Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Quiet lot");

        Watcher watcher = watch(auction);

        bid(auction, bidder, "9999999", 422);

        // EN: This is what AFTER_COMMIT buys. Announcing a price the transaction threw away would leave
        //     every open page showing a number the database never held.
        // VI: Đây là thứ AFTER_COMMIT mang lại. Loan báo một mức giá mà transaction đã vứt đi sẽ khiến mọi
        //     trang đang mở hiển thị con số database chưa từng có.
        assertThat(watcher.heardNothingWithin(Duration.ofSeconds(2))).isTrue();

        watcher.session().disconnect();
    }

    @Test
    void aBidThatIsRolledBackAfterwardsIsBroadcastToNobody() throws Exception {
        String seller = tokenFor("rt.seller6@nexbid.com", "Sixth Seller", RoleName.SELLER);
        String admin = tokenFor("rt.admin6@nexbid.com", "Sixth Admin", RoleName.ADMIN);
        String bidder = tokenFor("rt.buyer6@nexbid.com", "Sixth Buyer", RoleName.BUYER);
        UUID auction = UUID.fromString(openLot(seller, admin, "Rolled back lot"));

        Watcher watcher = watch(auction.toString());

        // EN: A bid that is accepted and then thrown away by the surrounding transaction. This is the one
        //     case that tells AFTER_COMMIT apart from a plain listener — the event is published either
        //     way, but only one of them keeps quiet when the database changes its mind.
        // VI: Một lượt trả giá được chấp nhận rồi bị transaction bao ngoài vứt bỏ. Đây là trường hợp duy
        //     nhất phân biệt AFTER_COMMIT với listener thường — sự kiện vẫn được phát ra ở cả hai, nhưng
        //     chỉ một bên chịu im lặng khi database đổi ý.
        UUID bidderId = resolveId(bidder);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            bids.place(auction, bidderId, new java.math.BigDecimal("10000000"));
            status.setRollbackOnly();
        });

        assertThat(watcher.heardNothingWithin(Duration.ofSeconds(2))).isTrue();

        // EN: And the database agrees there was never a bid.
        // VI: Và database cũng xác nhận chưa từng có lượt trả giá nào.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM bids WHERE auction_id = ?", Integer.class, auction)).isZero();

        watcher.session().disconnect();
    }

    /** EN: The account id behind a token. / VI: Id tài khoản đứng sau một token. */
    private UUID resolveId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(objectMapper.readTree(body).get("data").get("id").asString());
    }

    @Test
    void aBidOnOneLotDoesNotReachAnotherLotsChannel() throws Exception {
        String seller = tokenFor("rt.seller4@nexbid.com", "Fourth Seller", RoleName.SELLER);
        String admin = tokenFor("rt.admin4@nexbid.com", "Fourth Admin", RoleName.ADMIN);
        String bidder = tokenFor("rt.buyer4@nexbid.com", "Fourth Buyer", RoleName.BUYER);
        String watched = openLot(seller, admin, "Lot one");
        String other = openLot(seller, admin, "Lot two");

        Watcher watcher = watch(other);

        bid(watched, bidder, "10000000", 201);

        // EN: One topic per lot, or a busy platform would deliver every lot's traffic to every page.
        // VI: Mỗi lô một topic, nếu không thì sàn đông khách sẽ dội lưu lượng của mọi lô vào mọi trang.
        assertThat(watcher.heardNothingWithin(Duration.ofSeconds(2))).isTrue();

        watcher.session().disconnect();
    }

    @Test
    void thePageCanWatchWithoutSigningIn() throws Exception {
        String seller = tokenFor("rt.seller5@nexbid.com", "Fifth Seller", RoleName.SELLER);
        String admin = tokenFor("rt.admin5@nexbid.com", "Fifth Admin", RoleName.ADMIN);
        String bidder = tokenFor("rt.buyer5@nexbid.com", "Fifth Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Open lot");

        // EN: watch() sends no token at all — the lot page is public, so watching it must be too.
        // VI: watch() không gửi token nào — trang lô là công khai, nên việc theo dõi nó cũng phải vậy.
        Watcher stranger = watch(auction);
        bid(auction, bidder, "10000000", 201);

        assertThat(stranger.awaitMessage()).isNotNull();

        stranger.session().disconnect();
    }

    @Test
    void aClientCannotBroadcastAPriceOfItsOwn() throws Exception {
        String seller = tokenFor("rt.seller7@nexbid.com", "Seventh Seller", RoleName.SELLER);
        String admin = tokenFor("rt.admin7@nexbid.com", "Seventh Admin", RoleName.ADMIN);
        String auction = openLot(seller, admin, "Impersonated lot");

        Watcher honest = watch(auction);
        Watcher liar = watch(auction);

        // EN: The broker relays whatever reaches it. Without an inbound guard this SEND lands in every
        //     subscriber's inbox, and every open page shows a price nobody ever offered.
        // VI: Broker chuyển tiếp bất cứ thứ gì tới được nó. Không có chốt chặn đầu vào, lệnh SEND này rơi
        //     vào hộp thư của mọi người đăng ký, và mọi trang đang mở hiện một mức giá chưa ai từng trả.
        liar.session().send("/topic/auctions/" + auction, Map.of(
                "type", "BID_PLACED", "currentPrice", 999999999, "bidCount", 99));

        assertThat(honest.heardNothingWithin(Duration.ofSeconds(2))).isTrue();

        honest.session().disconnect();
    }

    @Test
    void aChannelForALotThatDoesNotExistSimplyStaysSilent() throws Exception {
        // EN: Subscribing is not a query — an in-memory broker has no idea which lots are real, and it
        //     should not, or the socket would become a second way to ask what exists.
        // VI: Đăng ký không phải là một truy vấn — broker trong bộ nhớ không biết lô nào có thật, và không
        //     nên biết, nếu không socket lại thành cách thứ hai để dò xem cái gì tồn tại.
        Watcher watcher = watch(UUID.randomUUID().toString());

        assertThat(watcher.heardNothingWithin(Duration.ofSeconds(1))).isTrue();

        watcher.session().disconnect();
    }
}
