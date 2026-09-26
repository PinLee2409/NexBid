package com.nexbid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * EN: The whole auction flow (guide §38): seller, product, auction, approval, bids, close, winner, payment.
 *     Everything is real — HTTP on a real port, the scheduler opening and closing the lot on its own
 *     clock, Postgres, Redis and Kafka in containers — and every step is checked through the public API,
 *     the way the frontend will see it.
 * VI: Toàn bộ luồng đấu giá (guide §38): người bán, sản phẩm, phiên, duyệt, trả giá, đóng phiên, người
 *     thắng, thanh toán. Mọi thứ đều thật — HTTP trên cổng thật, scheduler tự mở và đóng phiên theo đồng hồ
 *     của nó, Postgres, Redis và Kafka trong container — và mỗi bước đều được kiểm qua API công khai, đúng
 *     như frontend sẽ thấy.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nexbid.scheduler.interval=250")
@Import(TestInfrastructure.class)
class AuctionFlowIntegrationTest {

    /** EN: A real 1x1 PNG, so the upload passes the magic-byte check. / VI: File PNG 1x1 thật, để qua bước kiểm byte. */
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
            0x42, 0x60, (byte) 0x82
    };

    @LocalServerPort
    private int port;

    @Autowired
    private UserService users;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private RestClient http;

    @BeforeEach
    void connect() {
        http = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    // ---------------------------------------------------------------- HTTP helpers

    private record Reply(int status, JsonNode body) {

        JsonNode data() {
            return body.get("data");
        }

        String code() {
            return body.get("code").asString();
        }
    }

    private record Person(String token, UUID id) {
    }

    private Reply call(HttpMethod method, String path, Person who, Object body) {
        RestClient.RequestBodySpec request = http.method(method).uri(path);
        if (who != null) {
            request.header("Authorization", "Bearer " + who.token());
        }
        if (body != null) {
            request.contentType(body instanceof MultiValueMap<?, ?> ? MediaType.MULTIPART_FORM_DATA : MediaType.APPLICATION_JSON)
                    .body(body);
        }
        return request.exchange((req, res) -> {
            byte[] content = res.getBody().readAllBytes();
            return new Reply(res.getStatusCode().value(), content.length == 0 ? null : json.readTree(content));
        });
    }

    private Reply get(String path, Person who) {
        return call(HttpMethod.GET, path, who, null);
    }

    private Reply post(String path, Person who, Map<String, ?> body) {
        return call(HttpMethod.POST, path, who, body);
    }

    private static boolean absent(JsonNode node, String field) {
        return node.get(field) == null || node.get(field).isNull();
    }

    private static List<JsonNode> list(JsonNode array) {
        List<JsonNode> items = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            items.add(array.get(i));
        }
        return items;
    }

    /**
     * EN: Signs up through the API. Granting a role is the one step with no public endpoint, so it goes
     *     through the service, as an operator would.
     * VI: Đăng ký qua API. Cấp vai trò là bước duy nhất không có endpoint công khai, nên đi qua service,
     *     như người vận hành sẽ làm.
     */
    private Person signUp(String email, String name, RoleName role) {
        assertThat(post("/api/auth/register", null,
                Map.of("fullName", name, "email", email, "password", "supersecret")).status()).isEqualTo(201);
        if (role != RoleName.BUYER) {
            users.grantRole(email, role);
        }
        Reply login = post("/api/auth/login", null, Map.of("email", email, "password", "supersecret"));
        assertThat(login.status()).isEqualTo(200);
        String token = login.data().get("accessToken").asString();
        UUID id = UUID.fromString(get("/api/users/me", new Person(token, null)).data().get("id").asString());
        return new Person(token, id);
    }

    private Reply bid(Person who, String auction, long amount) {
        return post("/api/auctions/" + auction + "/bids", who, Map.of("amount", amount));
    }

    private String publicStatus(String auction) {
        Reply lot = get("/api/auctions/" + auction, null);
        return lot.status() == 200 ? lot.data().get("auction").get("status").asString() : "HTTP " + lot.status();
    }

    private List<String> noticesOf(Person who) {
        return list(get("/api/notifications", who).data().get("notifications").get("items")).stream()
                .map(notice -> notice.get("type").asString()).toList();
    }

    private String listProduct(Person seller, String name) {
        String category = get("/api/categories", null).data().get(0).get("id").asString();
        Reply product = post("/api/seller/products", seller, Map.of(
                "name", name, "description", "A long description of the item.",
                "categoryId", category, "condition", "LIKE_NEW", "publishNow", true));
        assertThat(product.status()).isEqualTo(201);
        return product.data().get("id").asString();
    }

    /** EN: Creates, submits and approves a lot that opens and closes on its own. / VI: Tạo, gửi duyệt và duyệt một lô tự mở và tự đóng. */
    private String scheduleLot(Person seller, Person admin, String product, Instant start, Instant end) {
        Reply created = post("/api/seller/auctions", seller, Map.of(
                "productId", product, "startingPrice", 10_000_000, "minimumIncrement", 500_000,
                "startTime", start.toString(), "endTime", end.toString()));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.data().get("status").asString()).isEqualTo("DRAFT");
        String auction = created.data().get("id").asString();

        Reply submitted = post("/api/seller/auctions/" + auction + "/submit", seller, null);
        assertThat(submitted.data().get("status").asString()).isEqualTo("PENDING_APPROVAL");
        // EN: Awaiting review, the lot is invisible to the public. / VI: Đang chờ duyệt thì công chúng chưa thấy lô.
        assertThat(publicStatus(auction)).isEqualTo("HTTP 404");

        assertThat(list(get("/api/admin/auctions/pending", admin).data()))
                .extracting(lot -> lot.get("id").asString()).contains(auction);
        Reply approved = post("/api/admin/auctions/" + auction + "/approve", admin, null);
        assertThat(approved.status()).isEqualTo(200);
        assertThat(approved.data().get("status").asString()).isEqualTo("SCHEDULED");
        return auction;
    }

    /** EN: A browser on the lot page, listening to the realtime feed. / VI: Một trình duyệt đang mở trang lô, nghe luồng realtime. */
    private BlockingQueue<Map<String, Object>> watch(String auction) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin("http://localhost:3000");
        StompSession session = client.connectAsync(
                        "ws://localhost:" + port + "/ws", handshake, new StompSessionHandlerAdapter() {})
                .get(10, TimeUnit.SECONDS);

        BlockingQueue<Map<String, Object>> feed = new LinkedBlockingQueue<>();
        session.subscribe("/topic/auctions/" + auction, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                feed.add((Map<String, Object>) payload);
            }
        });
        // EN: Give the SUBSCRIBE frame time to reach the broker. / VI: Cho frame SUBSCRIBE kịp tới broker.
        Thread.sleep(300);
        return feed;
    }

    private static List<String> drain(BlockingQueue<Map<String, Object>> feed, int count) throws InterruptedException {
        List<String> types = new ArrayList<>();
        while (types.size() < count) {
            Map<String, Object> message = feed.poll(15, TimeUnit.SECONDS);
            if (message == null) {
                break;
            }
            types.add((String) message.get("type"));
        }
        return types;
    }

    // ---------------------------------------------------------------- the flow

    @Test
    void aLotGoesFromTheSellersProductToAPaidOrder() throws Exception {
        Person seller = signUp("flow.seller@nexbid.com", "Flow Seller", RoleName.SELLER);
        Person admin = signUp("flow.admin@nexbid.com", "Flow Admin", RoleName.ADMIN);
        Person anna = signUp("flow.anna@nexbid.com", "Anna Flow", RoleName.BUYER);
        Person ben = signUp("flow.ben@nexbid.com", "Ben Flow", RoleName.BUYER);

        // EN: 1. The seller lists a product with a photo. / VI: 1. Người bán đăng sản phẩm kèm ảnh.
        String product = listProduct(seller, "Leica M3");
        MultipartBodyBuilder photo = new MultipartBodyBuilder();
        photo.part("files", new ByteArrayResource(PNG)).filename("front.png").contentType(MediaType.IMAGE_PNG);
        assertThat(call(HttpMethod.POST, "/api/seller/products/" + product + "/images", seller, photo.build()).status())
                .isEqualTo(201);

        // EN: 2-3. An auction, submitted and approved. It opens in 3 s and runs for 10 s.
        // VI: 2-3. Một phiên, gửi duyệt và được duyệt. Mở sau 3 giây và chạy trong 10 giây.
        Instant start = Instant.now().plusSeconds(3);
        Instant end = start.plusSeconds(10);
        String auction = scheduleLot(seller, admin, product, start, end);
        assertThat(publicStatus(auction)).isEqualTo("SCHEDULED");
        BlockingQueue<Map<String, Object>> page = watch(auction);

        // EN: Not open yet, so no bids. / VI: Chưa mở nên chưa nhận trả giá.
        Reply early = bid(anna, auction, 10_000_000);
        assertThat(early.status()).isEqualTo(409);
        assertThat(early.code()).isEqualTo("AUCTION_NOT_ACTIVE");

        // EN: 4. The scheduler opens it on time. / VI: 4. Scheduler mở phiên đúng giờ.
        await().atMost(Duration.ofSeconds(10)).until(() -> publicStatus(auction).equals("ACTIVE"));
        JsonNode detail = get("/api/auctions/" + auction, null).data();
        assertThat(detail.get("product").get("name").asString()).isEqualTo("Leica M3");
        assertThat(detail.get("images").size()).isEqualTo(1);

        // EN: 5. Bidding, with the rules that guard it. / VI: 5. Trả giá, cùng các luật canh giữ nó.
        Reply own = bid(seller, auction, 10_000_000);
        assertThat(own.status()).isEqualTo(403);
        assertThat(own.code()).isEqualTo("SELLER_CANNOT_BID");

        assertThat(bid(anna, auction, 10_000_000).status()).isEqualTo(201);
        Reply tooLow = bid(ben, auction, 10_200_000);
        assertThat(tooLow.status()).isEqualTo(422);
        assertThat(tooLow.code()).isEqualTo("BID_TOO_LOW");
        assertThat(bid(ben, auction, 10_500_000).status()).isEqualTo(201);
        Reply lead = bid(anna, auction, 11_000_000);
        assertThat(lead.status()).isEqualTo(201);
        assertThat(lead.data().get("leading").asBoolean()).isTrue();
        // EN: Redis counted all three of Anna's bid requests, the refused early one too (spec §20.2).
        // VI: Redis đếm đủ ba request trả giá của Anna, kể cả lần bị từ chối vì quá sớm (spec §20.2).
        assertThat(redis.opsForZSet().zCard("rate:bid:" + anna.id())).isEqualTo(3);

        JsonNode history = get("/api/auctions/" + auction + "/bids", null).data();
        assertThat(list(history.get("items"))).extracting(line -> line.get("amount").decimalValue().longValue())
                .containsExactly(11_000_000L, 10_500_000L, 10_000_000L);

        // EN: Each was told when they lost the lead — through the outbox and Kafka.
        // VI: Mỗi người được báo khi mất vị trí dẫn đầu — qua outbox và Kafka.
        await().atMost(Duration.ofSeconds(15)).until(() -> noticesOf(anna).contains("OUTBID")
                && noticesOf(ben).contains("OUTBID"));

        // EN: 6. The scheduler closes it at the end time; the top bidder wins.
        // VI: 6. Scheduler đóng phiên đúng giờ kết thúc; người trả cao nhất thắng.
        await().atMost(Duration.ofSeconds(25)).until(() -> publicStatus(auction).equals("ENDED"));
        assertThat(Instant.now()).isAfterOrEqualTo(end);

        Reply late = bid(ben, auction, 12_000_000);
        assertThat(late.status()).isEqualTo(409);
        assertThat(late.code()).isEqualTo("AUCTION_ALREADY_ENDED");

        JsonNode closed = get("/api/auctions/" + auction, null).data().get("auction");
        assertThat(closed.get("currentPrice").decimalValue()).isEqualByComparingTo("11000000");
        assertThat(closed.get("bidCount").asInt()).isEqualTo(3);
        // EN: The public page never names the winner. / VI: Trang công khai không bao giờ nêu tên người thắng.
        assertThat(absent(closed, "winnerId")).isTrue();

        assertThat(list(get("/api/users/me/wins", anna).data()))
                .extracting(win -> win.get("auction").get("id").asString()).containsExactly(auction);
        assertThat(get("/api/users/me/wins", ben).data().size()).isZero();

        await().atMost(Duration.ofSeconds(15)).until(() -> noticesOf(anna).contains("AUCTION_WON")
                && noticesOf(ben).contains("AUCTION_LOST"));

        // EN: Everyone watching the page saw the same story, in order. / VI: Ai đang xem trang cũng thấy cùng câu chuyện, đúng thứ tự.
        assertThat(drain(page, 5)).containsExactly(
                "AUCTION_STARTED", "BID_PLACED", "BID_PLACED", "BID_PLACED", "AUCTION_ENDED");

        // EN: 7. The winner pays; nobody else can. / VI: 7. Người thắng trả tiền; không ai khác làm được.
        List<JsonNode> annasPayments = list(get("/api/users/me/payments", anna).data());
        assertThat(annasPayments).hasSize(1);
        JsonNode payment = annasPayments.getFirst().get("payment");
        String paymentId = payment.get("id").asString();
        assertThat(payment.get("status").asString()).isEqualTo("PENDING");
        assertThat(payment.get("amount").decimalValue()).isEqualByComparingTo("11000000");
        assertThat(Instant.parse(payment.get("expiredAt").asString())).isAfter(end.plus(Duration.ofHours(47)));

        assertThat(get("/api/users/me/payments", ben).data().size()).isZero();
        Reply stolen = post("/api/payments/" + paymentId + "/pay", ben, Map.of("outcome", "SUCCESS"));
        assertThat(stolen.status()).isEqualTo(404);
        assertThat(stolen.code()).isEqualTo("PAYMENT_NOT_FOUND");

        Reply paid = post("/api/payments/" + paymentId + "/pay", anna, Map.of("outcome", "SUCCESS"));
        assertThat(paid.status()).isEqualTo(200);
        assertThat(paid.data().get("payment").get("status").asString()).isEqualTo("SUCCESS");

        assertThat(publicStatus(auction)).isEqualTo("COMPLETED");
        List<JsonNode> orders = list(get("/api/users/me/orders", anna).data());
        assertThat(orders).hasSize(1);
        assertThat(orders.getFirst().get("order").get("status").asString()).isEqualTo("PAID");
        assertThat(orders.getFirst().get("order").get("sellerId").asString()).isEqualTo(seller.id().toString());
        assertThat(orders.getFirst().get("order").get("amount").decimalValue()).isEqualByComparingTo("11000000");
        await().atMost(Duration.ofSeconds(15)).until(() -> noticesOf(anna).contains("PAYMENT_SUCCESS"));

        // EN: 8. The admin can trace every step. / VI: 8. Admin lần lại được từng bước.
        List<JsonNode> audit = list(get("/api/admin/audit-logs?size=100", admin).data().get("items")).reversed();
        assertThat(audit.stream().filter(entry -> entry.get("entityId").asString().equals(auction)))
                .extracting(entry -> entry.get("action").asString(),
                        entry -> absent(entry, "userId") ? "system" : entry.get("userId").asString())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AUCTION_CREATED", seller.id().toString()),
                        org.assertj.core.groups.Tuple.tuple("AUCTION_APPROVED", admin.id().toString()),
                        org.assertj.core.groups.Tuple.tuple("AUCTION_ENDED", "system"));
        assertThat(audit.stream().filter(entry -> entry.get("action").asString().equals("BID_PLACED")))
                .extracting(entry -> entry.get("userId").asString())
                .containsExactly(anna.id().toString(), ben.id().toString(), anna.id().toString());
        assertThat(audit.stream().filter(entry -> entry.get("action").asString().equals("PAYMENT_SUCCESS")))
                .extracting(entry -> entry.get("entityId").asString(), entry -> entry.get("userId").asString())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(paymentId, anna.id().toString()));

        // EN: 9. And the infrastructure did its part: the lot card cached, no event left behind in the outbox.
        // VI: 9. Và hạ tầng làm đúng phần mình: thẻ lô được cache, không sự kiện nào bị bỏ lại trong outbox.
        assertThat(redis.hasKey("nexbid:v1:lot:" + auction)).isTrue();
        await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class) == 0);
    }

    @Test
    void aLotNobodyBidsOnEndsWithoutAWinnerAndTheProductCanBeSoldAgain() {
        Person seller = signUp("quiet.seller@nexbid.com", "Quiet Seller", RoleName.SELLER);
        Person admin = signUp("quiet.admin@nexbid.com", "Quiet Admin", RoleName.ADMIN);
        String product = listProduct(seller, "Hasselblad 500C");

        Instant start = Instant.now().plusSeconds(2);
        String auction = scheduleLot(seller, admin, product, start, start.plusSeconds(3));

        await().atMost(Duration.ofSeconds(20)).until(() -> publicStatus(auction).equals("ENDED"));

        JsonNode closed = get("/api/seller/auctions/" + auction, seller).data();
        assertThat(closed.get("bidCount").asInt()).isZero();
        assertThat(absent(closed, "winnerId")).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE auction_id = ?::uuid",
                Integer.class, auction)).isZero();

        // EN: Unsold, the product is the seller's again, free to go up for another auction.
        // VI: Không bán được, sản phẩm lại thuộc quyền người bán, được đưa lên một phiên khác.
        assertThat(get("/api/seller/products/" + product, seller).data().get("status").asString()).isEqualTo("AVAILABLE");
        Reply again = post("/api/seller/auctions", seller, Map.of(
                "productId", product, "startingPrice", 9_000_000, "minimumIncrement", 500_000,
                "startTime", Instant.now().plusSeconds(3600).toString(),
                "endTime", Instant.now().plusSeconds(7200).toString()));
        assertThat(again.status()).isEqualTo(201);
    }
}
