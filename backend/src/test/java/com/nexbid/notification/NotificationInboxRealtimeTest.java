package com.nexbid.notification;

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
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The bell's realtime channel (spec §18): a signed-in socket hears its own notices the moment they are
 *     written, and nobody else's; a socket without a usable token has no inbox at all.
 * VI: Kênh realtime của cái chuông (spec §18): socket đã đăng nhập nghe thông báo của chính mình ngay khi được
 *     ghi, và không nghe của ai khác; socket không có token dùng được thì không có hộp thư nào.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class NotificationInboxRealtimeTest {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    /** EN: One socket: what it heard on its inbox, and the errors the server sent it. / VI: Một socket: những gì nó nghe ở hộp thư, và các lỗi server gửi cho nó. */
    private record Socket(StompSession session, BlockingQueue<Map<String, Object>> inbox, BlockingQueue<String> errors) {

        Map<String, Object> awaitNotice() throws InterruptedException {
            return inbox.poll(15, TimeUnit.SECONDS);
        }

        boolean heardNothingWithin(Duration window) throws InterruptedException {
            return inbox.poll(window.toMillis(), TimeUnit.MILLISECONDS) == null;
        }
    }

    private Socket connect(String token, String destination) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());

        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin("http://localhost:3000");
        StompHeaders connect = new StompHeaders();
        if (token != null) {
            connect.add("Authorization", "Bearer " + token);
        }

        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", handshake, connect,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public Type getPayloadType(StompHeaders headers) {
                                return byte[].class;
                            }

                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                // EN: The session handler receives the server's ERROR frames. / VI: Handler của session nhận các frame ERROR của server.
                                errors.add(String.valueOf(headers.getFirst("message")));
                            }
                        })
                .get(10, TimeUnit.SECONDS);

        BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
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

        return new Socket(session, inbox, errors);
    }

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

    private UUID idOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("data").get("id").asString());
    }

    private String activeLot(String seller, String admin, String name) throws Exception {
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
        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 minute' WHERE id = ?::uuid",
                auctionId);
        return auctionId;
    }

    private void bid(String auctionId, String token, String amount) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void eachPersonHearsTheirOwnNoticeTheMomentItIsWritten() throws Exception {
        String seller = tokenFor("inbox.seller@nexbid.com", "Inbox Seller", RoleName.SELLER);
        String admin = tokenFor("inbox.admin@nexbid.com", "Inbox Admin", RoleName.ADMIN);
        String anna = tokenFor("inbox.anna@nexbid.com", "Anna Inbox", RoleName.BUYER);
        String ben = tokenFor("inbox.ben@nexbid.com", "Ben Inbox", RoleName.BUYER);
        String lot = activeLot(seller, admin, "Inbox lot");

        Socket annaBell = connect(anna, NotificationChannel.SUBSCRIPTION);
        Socket benBell = connect(ben, NotificationChannel.SUBSCRIPTION);

        bid(lot, anna, "10000000");
        bid(lot, ben, "10500000");

        Map<String, Object> notice = annaBell.awaitNotice();
        assertThat(notice).isNotNull();
        assertThat(notice).containsEntry("type", "OUTBID").containsEntry("auctionId", lot).containsEntry("read", false);
        // EN: The pushed notice is the stored one, so the page can mark it read. / VI: Thông báo được đẩy chính là bản đã lưu, nên trang đánh dấu đã đọc được.
        assertThat(jdbc.queryForObject("SELECT user_id FROM notifications WHERE id = ?::uuid",
                UUID.class, notice.get("id"))).isEqualTo(idOf(anna));

        // EN: Ben caused it; he was told nothing. / VI: Ben gây ra nó; Ben không được báo gì.
        assertThat(benBell.heardNothingWithin(Duration.ofSeconds(2))).isTrue();
        assertThat(annaBell.errors()).isEmpty();

        annaBell.session().disconnect();
        benBell.session().disconnect();
    }

    @Test
    void aSocketWithoutAUsableTokenHasNoInbox() throws Exception {
        Socket anonymous = connect(null, NotificationChannel.SUBSCRIPTION);
        assertThat(anonymous.errors().poll(10, TimeUnit.SECONDS)).contains("No such destination");

        Socket forged = connect("not-a-token", NotificationChannel.SUBSCRIPTION);
        assertThat(forged.errors().poll(10, TimeUnit.SECONDS)).contains("No such destination");
    }

    @Test
    void nobodyCanSubscribeToSomeoneElsesInboxByName() throws Exception {
        String eve = tokenFor("inbox.eve@nexbid.com", "Eve Curious", RoleName.BUYER);
        String victim = tokenFor("inbox.victim@nexbid.com", "Victim Inbox", RoleName.BUYER);

        Socket snooping = connect(eve, "/user/" + idOf(victim) + NotificationChannel.QUEUE);
        assertThat(snooping.errors().poll(10, TimeUnit.SECONDS)).contains("No such destination");

        Socket direct = connect(eve, NotificationChannel.QUEUE);
        assertThat(direct.errors().poll(10, TimeUnit.SECONDS)).contains("No such destination");
    }
}
