package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
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
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Opening lots on time (guide §25). The scheduler is switched off here and the rule is called
 *     directly, so every test decides exactly when "now" happens instead of racing a background thread.
 * VI: Mở lô đúng giờ (guide §25). Ở đây tắt scheduler và gọi thẳng luật, để mỗi test tự quyết "bây giờ"
 *     là lúc nào thay vì chạy đua với một luồng chạy nền.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class AuctionAutoStartTest {

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

    /** EN: Approved and waiting for its start time. / VI: Đã duyệt và đang chờ tới giờ mở. */
    private String scheduledLot(String seller, String admin, String name) throws Exception {
        String auctionId = submittedLot(seller, name);

        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

        return auctionId;
    }

    /** EN: Winds the start time back so the lot is due. / VI: Vặn lùi giờ mở để lô tới hạn. */
    private void makeDue(String auctionId) {
        jdbc.update("UPDATE auctions SET start_time = now() - interval '1 second' WHERE id = ?::uuid",
                auctionId);
    }

    private String statusOf(String auctionId) {
        return jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, auctionId);
    }

    /** EN: Clears anything an earlier test left due. / VI: Dọn mọi thứ test trước để lại đã tới hạn. */
    private void drain() {
        while (auctions.startDueAuctions(Instant.now(), 500) > 0) {
            // EN: Keep going until nothing is due. / VI: Chạy tiếp tới khi không còn gì tới hạn.
        }
    }

    @Test
    void aLotWhoseStartTimeHasArrivedOpens() throws Exception {
        String seller = tokenFor("start.seller1@nexbid.com", "Start Seller", RoleName.SELLER);
        String admin = tokenFor("start.admin1@nexbid.com", "Start Admin", RoleName.ADMIN);
        String buyer = tokenFor("start.buyer1@nexbid.com", "Start Buyer", RoleName.BUYER);
        String auction = scheduledLot(seller, admin, "Due lot");

        // EN: Before its time, a bid is refused.
        // VI: Chưa tới giờ thì lượt trả giá bị từ chối.
        mockMvc.perform(post("/api/auctions/" + auction + "/bids")
                        .header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000000}"))
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_ACTIVE"));

        makeDue(auction);
        auctions.startDueAuctions(Instant.now(), 200);

        assertThat(statusOf(auction)).isEqualTo("ACTIVE");

        // EN: The point of opening it: the same bid now goes through.
        // VI: Mục đích của việc mở: cùng lượt trả giá đó giờ đã được nhận.
        mockMvc.perform(get("/api/auctions/" + auction))
                .andExpect(jsonPath("$.data.openForBidding").value(true));

        mockMvc.perform(post("/api/auctions/" + auction + "/bids")
                        .header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000000}"))
                .andExpect(status().isCreated());
    }

    @Test
    void aLotWhoseStartTimeIsStillAheadIsLeftAlone() throws Exception {
        String seller = tokenFor("start.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("start.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String auction = scheduledLot(seller, admin, "Future lot");

        auctions.startDueAuctions(Instant.now(), 200);

        assertThat(statusOf(auction)).isEqualTo("SCHEDULED");
    }

    @Test
    void aLotWhoseWholeWindowHasPassedIsNotOpened() throws Exception {
        String seller = tokenFor("start.seller7@nexbid.com", "Seventh Seller", RoleName.SELLER);
        String admin = tokenFor("start.admin7@nexbid.com", "Seventh Admin", RoleName.ADMIN);
        String auction = scheduledLot(seller, admin, "Missed lot");

        // EN: The server was down for the lot's entire window — found in real dev data, not invented.
        //     Opening it now would list an "active" lot that refuses every bid; closing it is the auto-end
        //     step's job.
        // VI: Server tắt trọn khung giờ của lô — phát hiện từ dữ liệu dev thật, không phải bịa ra. Mở nó bây
        //     giờ sẽ liệt kê một lô "đang mở" mà từ chối mọi lượt trả giá; đóng nó là việc của bước tự kết thúc.
        jdbc.update("UPDATE auctions SET start_time = now() - interval '5 hours', "
                + "end_time = now() - interval '4 hours' WHERE id = ?::uuid", auction);

        auctions.startDueAuctions(Instant.now(), 200);

        assertThat(statusOf(auction)).isEqualTo("SCHEDULED");
    }

    @Test
    void onlyApprovedLotsAreEverOpened() throws Exception {
        String seller = tokenFor("start.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String pending = submittedLot(seller, "Still in review");
        makeDue(pending);

        auctions.startDueAuctions(Instant.now(), 200);

        // EN: The clock does not get to skip review. A lot nobody approved stays exactly where it was,
        //     however late it is.
        // VI: Đồng hồ không được phép bỏ qua bước duyệt. Lô chưa ai duyệt nằm yên chỗ cũ, dù trễ tới đâu.
        assertThat(statusOf(pending)).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    void aLargeBacklogIsWorkedThroughInBatches() throws Exception {
        String seller = tokenFor("start.seller4@nexbid.com", "Fourth Seller", RoleName.SELLER);
        String admin = tokenFor("start.admin4@nexbid.com", "Fourth Admin", RoleName.ADMIN);

        drain();

        String first = scheduledLot(seller, admin, "Backlog one");
        String second = scheduledLot(seller, admin, "Backlog two");
        String third = scheduledLot(seller, admin, "Backlog three");
        makeDue(first);
        makeDue(second);
        makeDue(third);

        // EN: Two per run: nothing is skipped, the cap only spreads the work across runs.
        // VI: Hai lô mỗi lượt: không lô nào bị bỏ sót, giới hạn chỉ chia việc ra nhiều lượt.
        assertThat(auctions.startDueAuctions(Instant.now(), 2)).isEqualTo(2);
        assertThat(auctions.startDueAuctions(Instant.now(), 2)).isEqualTo(1);
        assertThat(auctions.startDueAuctions(Instant.now(), 2)).isZero();

        assertThat(statusOf(first)).isEqualTo("ACTIVE");
        assertThat(statusOf(second)).isEqualTo("ACTIVE");
        assertThat(statusOf(third)).isEqualTo("ACTIVE");
    }

    @Test
    void runningAgainChangesNothing() throws Exception {
        String seller = tokenFor("start.seller5@nexbid.com", "Fifth Seller", RoleName.SELLER);
        String admin = tokenFor("start.admin5@nexbid.com", "Fifth Admin", RoleName.ADMIN);

        drain();

        String auction = scheduledLot(seller, admin, "Opened once");
        makeDue(auction);

        assertThat(auctions.startDueAuctions(Instant.now(), 200)).isEqualTo(1);

        // EN: Runs every few seconds forever, so a lot that is already open must not be opened again.
        // VI: Chạy mỗi vài giây mãi mãi, nên lô đã mở rồi không được mở thêm lần nữa.
        assertThat(auctions.startDueAuctions(Instant.now(), 200)).isZero();
        assertThat(statusOf(auction)).isEqualTo("ACTIVE");
    }

    @Test
    void everyoneWatchingTheLotIsToldItOpened() throws Exception {
        String seller = tokenFor("start.seller6@nexbid.com", "Sixth Seller", RoleName.SELLER);
        String admin = tokenFor("start.admin6@nexbid.com", "Sixth Admin", RoleName.ADMIN);
        String auction = scheduledLot(seller, admin, "Announced lot");

        BlockingQueue<Map<String, Object>> inbox = watch(auction);

        makeDue(auction);
        auctions.startDueAuctions(Instant.now(), 200);

        Map<String, Object> message = inbox.poll(10, TimeUnit.SECONDS);

        // EN: Without this the page keeps counting down to a start that already happened, with the bid
        //     button still greyed out.
        // VI: Thiếu bản tin này, trang vẫn đếm ngược tới giờ mở đã qua, và nút trả giá vẫn xám.
        assertThat(message).isNotNull();
        assertThat(message).containsEntry("type", "AUCTION_STARTED");
        assertThat(message).containsEntry("auctionId", auction);
        assertThat(message).containsEntry("status", "ACTIVE");
        assertThat(message.get("endTime")).isNotNull();
        assertThat(message.get("serverTime")).isNotNull();

        // EN: And only once — the next run finds nothing to announce.
        // VI: Và chỉ một lần — lượt chạy sau không tìm thấy gì để loan báo.
        auctions.startDueAuctions(Instant.now(), 200);
        assertThat(inbox.poll(1500, TimeUnit.MILLISECONDS)).isNull();
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

        // EN: Give the SUBSCRIBE frame time to reach the broker before anything is published.
        // VI: Cho frame SUBSCRIBE kịp tới broker trước khi có gì được phát ra.
        Thread.sleep(300);

        return inbox;
    }
}
