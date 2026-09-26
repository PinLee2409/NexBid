package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
 * EN: Online viewer count (spec §20.3): every page watching a lot counts once, leaving takes it off again,
 *     and the lot's channel and its public detail agree on the number.
 * VI: Số người đang xem (spec §20.3): mỗi trang đang xem một lô được tính một lần, rời đi thì bị trừ lại, và
 *     kênh của lô cùng trang chi tiết công khai nói cùng một con số.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class LotViewersTest {

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

    private record Viewer(StompSession session, BlockingQueue<Map<String, Object>> heard) {

        /** EN: Waits for the count to read `expected`, skipping older counts on the way. / VI: Chờ số đếm thành `expected`, bỏ qua các số cũ trên đường. */
        boolean hearsCount(int expected) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                Map<String, Object> message = heard.poll(500, TimeUnit.MILLISECONDS);
                if (message != null && ViewerCountMessage.TYPE.equals(message.get("type"))
                        && ((Number) message.get("viewerCount")).intValue() == expected) {
                    return true;
                }
            }
            return false;
        }
    }

    private Viewer view(String auctionId) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin("http://localhost:3000");

        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", handshake,
                        new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);

        BlockingQueue<Map<String, Object>> heard = new LinkedBlockingQueue<>();
        session.subscribe(AuctionChannel.topicFor(java.util.UUID.fromString(auctionId)), new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                heard.add((Map<String, Object>) payload);
            }
        });
        return new Viewer(session, heard);
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

    private String activeLot(String name) throws Exception {
        String seller = tokenFor("view.seller." + name + "@nexbid.com", "Viewer Seller", RoleName.SELLER);
        String admin = tokenFor("view.admin." + name + "@nexbid.com", "Viewer Admin", RoleName.ADMIN);
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

    private void detailSays(String auctionId, int viewers) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/api/auctions/" + auctionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.viewerCount").value(viewers)));
    }

    @Test
    void everyOpenPageCountsAndLeavingTakesItOff() throws Exception {
        String lot = activeLot("counted");
        detailSays(lot, 0);

        Viewer first = view(lot);
        Viewer second = view(lot);

        assertThat(first.hearsCount(2)).isTrue();
        detailSays(lot, 2);

        second.session().disconnect();
        assertThat(first.hearsCount(1)).isTrue();
        detailSays(lot, 1);

        first.session().disconnect();
        detailSays(lot, 0);
    }

    @Test
    void viewersOfOneLotDoNotCountOnAnother() throws Exception {
        String watched = activeLot("watched");
        String quiet = activeLot("quiet");

        Viewer viewer = view(watched);
        assertThat(viewer.hearsCount(1)).isTrue();
        detailSays(quiet, 0);

        viewer.session().disconnect();
    }
}
