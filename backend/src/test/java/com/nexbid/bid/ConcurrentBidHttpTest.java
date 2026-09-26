package com.nexbid.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.nexbid.auth.jwt.JwtService;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserCredentials;
import com.nexbid.user.UserStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * EN: The concurrency test from guide §39 and spec §32, over real HTTP: the price stands at 20m with a 1m
 *     step, and a hundred people send 21m at the same instant. Exactly one may win, the price must end at
 *     21m, and everything a bid sets off — the broadcast, the audit row, the outbid notice — must happen
 *     once, not a hundred times.
 * VI: Bài test đồng thời của guide §39 và spec §32, qua HTTP thật: giá đang 20tr với bước giá 1tr, và một
 *     trăm người cùng gửi 21tr trong cùng một khoảnh khắc. Chỉ đúng một người được thắng, giá phải dừng ở
 *     21tr, và mọi thứ một lượt trả giá kéo theo — bản tin, dòng audit, thông báo bị vượt giá — phải xảy ra
 *     một lần, không phải một trăm lần.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "nexbid.scheduler.enabled=false")
@Import(TestInfrastructure.class)
class ConcurrentBidHttpTest {

    private static final int RACERS = 100;
    private static final BigDecimal TWENTY_MILLION = new BigDecimal("20000000");
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwt;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private record Person(UUID id, String token) {
    }

    private record Answer(UUID bidder, BigDecimal amount, int status, String code) {
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * EN: Account rows go straight into the table and tokens come from the same service login uses —
     *     a hundred BCrypt hashes would only slow the setup, not test anything.
     * VI: Dòng tài khoản ghi thẳng vào bảng và token lấy từ chính service mà login dùng — một trăm lần hash
     *     BCrypt chỉ làm chậm phần chuẩn bị, không kiểm được gì thêm.
     */
    private Person person(String name, RoleName role) {
        UUID id = UUID.randomUUID();
        String email = name.toLowerCase().replace(' ', '.') + "." + id + "@nexbid.com";
        jdbc.update("""
                INSERT INTO users (id, full_name, email, password, status, created_at, updated_at)
                VALUES (?, ?, ?, 'x', 'ACTIVE', now(), now())
                """, id, name, email);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT ?, id FROM roles WHERE name = ?", id, role.name());
        String token = jwt.issue(new UserCredentials(id, name, email, "x", UserStatus.ACTIVE, Set.of(role)));
        return new Person(id, token);
    }

    private HttpResponse<String> send(String method, String path, Person who, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + who.token())
                .timeout(Duration.ofSeconds(60));
        request.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode data(HttpResponse<String> response) {
        return json.readTree(response.body()).get("data");
    }

    /** EN: An open lot whose opening price is 20m and whose step is 1m. / VI: Một lô đang mở, giá khởi điểm 20tr, bước giá 1tr. */
    private String openLot(Person seller, Person admin) throws Exception {
        String category = jdbc.queryForObject("SELECT id::text FROM categories LIMIT 1", String.class);
        String product = data(send("POST", "/api/seller/products", seller, """
                {"name":"Nikon F","description":"A long description of the item.",
                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                """.formatted(category))).get("id").asString();
        String auction = data(send("POST", "/api/seller/auctions", seller, """
                {"productId":"%s","startingPrice":%s,"minimumIncrement":%s,"startTime":"%s","endTime":"%s"}
                """.formatted(product, TWENTY_MILLION, ONE_MILLION,
                Instant.now().plus(Duration.ofMinutes(30)), Instant.now().plus(Duration.ofHours(4)))))
                .get("id").asString();
        send("POST", "/api/seller/auctions/" + auction + "/submit", seller, null);
        assertThat(send("POST", "/api/admin/auctions/" + auction + "/approve", admin, null).statusCode()).isEqualTo(200);
        // EN: The scheduler is off here; open the lot now rather than wait half an hour.
        // VI: Ở đây tắt scheduler; mở lô ngay thay vì chờ nửa tiếng.
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 minute' WHERE id = ?::uuid",
                auction);
        return auction;
    }

    /**
     * EN: Everyone sends at once: all requests are built first, then one gate releases them together.
     * VI: Mọi người gửi cùng lúc: dựng sẵn mọi request, rồi một cổng thả tất cả ra cùng nhau.
     */
    private List<Answer> race(String auction, List<Person> racers, List<BigDecimal> amounts) throws Exception {
        List<Answer> answers = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch gate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers.size());

        try (ExecutorService pool = Executors.newFixedThreadPool(racers.size())) {
            for (int i = 0; i < racers.size(); i++) {
                Person racer = racers.get(i);
                BigDecimal amount = amounts.get(i);
                pool.submit(() -> {
                    try {
                        gate.await();
                        HttpResponse<String> response = send("POST", "/api/auctions/" + auction + "/bids", racer,
                                "{\"amount\":" + amount.toPlainString() + "}");
                        JsonNode body = json.readTree(response.body());
                        answers.add(new Answer(racer.id(), amount, response.statusCode(),
                                body.has("code") ? body.get("code").asString() : null));
                    } catch (Exception ex) {
                        answers.add(new Answer(racer.id(), amount, -1, ex.toString()));
                    } finally {
                        done.countDown();
                    }
                });
            }
            gate.countDown();
            assertThat(done.await(90, TimeUnit.SECONDS)).isTrue();
        }
        return answers;
    }

    /** EN: A browser on the lot page. / VI: Một trình duyệt đang mở trang lô. */
    private BlockingQueue<Map<String, Object>> watch(String auction) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin("http://localhost:3000");
        StompSession session = client.connectAsync(url("/ws").replace("http", "ws"), handshake,
                new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);

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
        Thread.sleep(300);
        return feed;
    }

    private Map<Integer, Long> byStatus(List<Answer> answers) {
        return answers.stream().collect(Collectors.groupingBy(Answer::status, Collectors.counting()));
    }

    private Person seller;
    private Person admin;
    private Person holder;
    private String auction;

    /** EN: The guide's starting point: one bid in, the price at 20m, the next valid bid 21m. / VI: Điểm xuất phát của guide: đã có một lượt, giá 20tr, lượt hợp lệ kế tiếp là 21tr. */
    @BeforeEach
    void priceStandsAtTwentyMillion() throws Exception {
        seller = person("Race Seller", RoleName.SELLER);
        admin = person("Race Admin", RoleName.ADMIN);
        holder = person("Price Holder", RoleName.BUYER);
        auction = openLot(seller, admin);

        HttpResponse<String> first = send("POST", "/api/auctions/" + auction + "/bids", holder, "{\"amount\":20000000}");
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(data(first).get("minimumNextBid").decimalValue()).isEqualByComparingTo("21000000");
    }

    @Test
    void aHundredPeopleBiddingTwentyOneMillionAtOnceProduceOneWinner() throws Exception {
        List<Person> racers = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            racers.add(person("Racer " + i, RoleName.BUYER));
        }
        BlockingQueue<Map<String, Object>> page = watch(auction);

        List<Answer> answers = race(auction, racers,
                Collections.nCopies(RACERS, new BigDecimal("21000000")));

        // EN: 1 success, 99 failures — and every failure is the rule speaking ("too low"), never a crash,
        //     a timeout or the rate limiter.
        // VI: 1 thành công, 99 thất bại — và mọi thất bại đều là luật lên tiếng ("quá thấp"), không bao giờ
        //     là lỗi hệ thống, timeout hay bộ giới hạn tần suất.
        assertThat(byStatus(answers)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 422, 99L));
        assertThat(answers.stream().filter(answer -> answer.status() == 422).map(Answer::code))
                .containsOnly("BID_TOO_LOW");
        UUID winner = answers.stream().filter(answer -> answer.status() == 201).findFirst().orElseThrow().bidder();

        // EN: The database agrees: 21m, two bids in total, the winner leading.
        // VI: Database khớp: 21tr, tổng cộng hai lượt, người thắng đang dẫn đầu.
        Map<String, Object> lot = jdbc.queryForMap(
                "SELECT current_price, bid_count, leading_bidder_id FROM auctions WHERE id = ?::uuid", auction);
        assertThat((BigDecimal) lot.get("current_price")).isEqualByComparingTo("21000000");
        assertThat(lot.get("bid_count")).isEqualTo(2);
        assertThat(lot.get("leading_bidder_id")).isEqualTo(winner);
        assertThat(jdbc.queryForList("SELECT bidder_id FROM bids WHERE auction_id = ?::uuid ORDER BY created_at",
                UUID.class, auction)).containsExactly(holder.id(), winner);

        // EN: Side effects happen once. The page sees one price change, not a hundred.
        // VI: Hiệu ứng kéo theo xảy ra một lần. Trang lô thấy một lần đổi giá, không phải một trăm.
        Map<String, Object> broadcast = page.poll(10, TimeUnit.SECONDS);
        assertThat(broadcast).isNotNull();
        assertThat(broadcast.get("type")).isEqualTo("BID_PLACED");
        assertThat(new BigDecimal(broadcast.get("currentPrice").toString())).isEqualByComparingTo("21000000");
        assertThat(page.poll(1500, TimeUnit.MILLISECONDS)).isNull();

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs a JOIN bids b ON b.id = a.entity_id
                 WHERE a.action = 'BID_PLACED' AND b.auction_id = ?::uuid
                """, Integer.class, auction)).isEqualTo(2);

        // EN: The holder hears once that they were outbid — through the outbox and Kafka.
        // VI: Người đang giữ giá chỉ nghe một lần rằng mình bị vượt — qua outbox và Kafka.
        await().atMost(Duration.ofSeconds(15)).until(() -> outbidNotices(holder) == 1);
        await().atMost(Duration.ofSeconds(10)).until(() ->
                jdbc.queryForObject("SELECT count(*) FROM outbox_events", Integer.class) == 0);
        assertThat(outbidNotices(holder)).isEqualTo(1);
    }

    @Test
    void aHundredDifferentOffersAtOnceLeaveAPriceThatOnlyEverRose() throws Exception {
        List<Person> racers = new ArrayList<>();
        List<BigDecimal> amounts = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            racers.add(person("Climber " + i, RoleName.BUYER));
            // EN: 21m, 22m … 120m: each valid against the 20m price, in a random arrival order.
            // VI: 21tr, 22tr … 120tr: mỗi số đều hợp lệ so với giá 20tr, tới theo thứ tự ngẫu nhiên.
            amounts.add(TWENTY_MILLION.add(ONE_MILLION.multiply(BigDecimal.valueOf(i + 1L))));
        }

        List<Answer> answers = race(auction, racers, amounts);

        // EN: Only the rule refuses — a bid that arrives after a higher one is simply too low by then.
        // VI: Chỉ có luật từ chối — lượt tới sau một lượt cao hơn thì lúc đó đơn giản là đã quá thấp.
        assertThat(answers).allSatisfy(answer -> assertThat(answer.status()).isIn(201, 422));
        assertThat(answers.stream().filter(answer -> answer.status() == 422).map(Answer::code)).containsOnly("BID_TOO_LOW");
        List<BigDecimal> accepted = answers.stream().filter(answer -> answer.status() == 201).map(Answer::amount).toList();
        assertThat(accepted).isNotEmpty();

        // EN: No lost update: every accepted bid is stored, counted, and in the order accepted each one beat
        //     the one before by at least the step. The price is the highest of them.
        // VI: Không mất lượt ghi nào: mọi lượt được nhận đều được lưu, được đếm, và theo thứ tự được nhận thì
        //     lượt sau vượt lượt trước ít nhất một bước giá. Giá cuối là lượt cao nhất trong số đó.
        List<BigDecimal> stored = jdbc.queryForList(
                "SELECT amount FROM bids WHERE auction_id = ?::uuid ORDER BY created_at", BigDecimal.class, auction);
        assertThat(stored).hasSize(accepted.size() + 1);
        for (int i = 1; i < stored.size(); i++) {
            assertThat(stored.get(i).subtract(stored.get(i - 1))).isGreaterThanOrEqualTo(ONE_MILLION);
        }
        BigDecimal highest = accepted.stream().max(BigDecimal::compareTo).orElseThrow();
        assertThat(stored.getLast()).isEqualByComparingTo(highest);
        assertThat(jdbc.queryForObject("SELECT current_price FROM auctions WHERE id = ?::uuid", BigDecimal.class, auction))
                .isEqualByComparingTo(highest);
        assertThat(jdbc.queryForObject("SELECT bid_count FROM auctions WHERE id = ?::uuid", Integer.class, auction))
                .isEqualTo(accepted.size() + 1);
    }

    private int outbidNotices(Person who) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'OUTBID'",
                Integer.class, who.id());
    }
}
