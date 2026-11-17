package com.nexbid.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.support.TestInfrastructure;
import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * EN: Browser push end to end against a push service played locally: the browser's keys decrypt what NexBid sent
 *     (RFC 8291), the VAPID token verifies with the key the browser subscribed with (RFC 8292), a gone subscription is
 *     forgotten, and outside tests only the known push services are posted to.
 * VI: Thông báo đẩy từ đầu tới cuối với một dịch vụ push chạy tại chỗ: khoá của trình duyệt giải mã được thứ NexBid gửi
 *     (RFC 8291), token VAPID xác minh được bằng khoá trình duyệt đã dùng khi đăng ký (RFC 8292), đăng ký đã mất thì bị
 *     quên, và ngoài test chỉ gửi tới các dịch vụ push đã biết.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class PushApiTest {

    private record Received(String authorization, String encoding, byte[] body) {
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationService notifications;

    @Autowired
    private PushService push;

    private String register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Push User","email":"%s","password":"supersecret"}""".formatted(email)));
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"supersecret"}""".formatted(email))).andReturn().getResponse()
                .getContentAsString();
        return "Bearer " + objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    @Test
    void aNoticeReachesTheBrowserEncryptedForItAndAGoneBrowserIsForgotten() throws Exception {
        List<Received> received = new CopyOnWriteArrayList<>();
        AtomicInteger answer = new AtomicInteger(201);
        HttpServer service = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        service.createContext("/push/", exchange -> {
            received.add(new Received(exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Encoding"), exchange.getRequestBody().readAllBytes()));
            exchange.sendResponseHeaders(answer.get(), -1);
            exchange.close();
        });
        service.start();
        try {
            String bearer = register("push.user@nexbid.com");
            UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'push.user@nexbid.com'", UUID.class);
            String publicKey = objectMapper.readTree(mockMvc.perform(get("/api/users/me/push/key")
                    .header("Authorization", bearer)).andReturn().getResponse().getContentAsString())
                    .get("data").get("publicKey").asString();

            KeyPair browser = WebPush.newKeyPair();
            byte[] auth = new byte[16];
            new SecureRandom().nextBytes(auth);
            String endpoint = "http://127.0.0.1:" + service.getAddress().getPort() + "/push/" + UUID.randomUUID();
            mockMvc.perform(post("/api/users/me/push").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"endpoint":"%s","p256dh":"%s","auth":"%s","locale":"en"}""".formatted(endpoint,
                                    WebPush.base64Url(WebPush.encode((ECPublicKey) browser.getPublic())),
                                    WebPush.base64Url(auth))))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/api/users/me/push").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"endpoint":"%s","p256dh":"bm90LWEta2V5","auth":"%s"}""".formatted(endpoint,
                                    WebPush.base64Url(auth))))
                    .andExpect(jsonPath("$.code").value("PUSH_SUBSCRIPTION_INVALID"));
            mockMvc.perform(post("/api/users/me/push/status").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"endpoint\":\"" + endpoint + "\"}"))
                    .andExpect(jsonPath("$.data.subscribed").value(true));

            notifications.notify(userId, NotificationType.OUTBID, "You were outbid", "Someone bid 1,200,000₫.", null,
                    Instant.now());
            await().atMost(Duration.ofSeconds(10)).until(() -> received.size() == 1);

            Received one = received.getFirst();
            assertThat(one.encoding()).isEqualTo("aes128gcm");
            JsonNode notice = objectMapper.readTree(decrypt(one.body(), browser, auth));
            assertThat(notice.get("title").asString()).isEqualTo("You were outbid");
            assertThat(notice.get("body").asString()).isEqualTo("Someone bid 1,200,000₫.");
            assertThat(notice.get("url").asString()).isEqualTo("/notifications");

            // EN: "vapid t=<jwt>, k=<key>": signed with the key the browser subscribed with, for this origin.
            // VI: "vapid t=<jwt>, k=<key>": ký bằng khoá trình duyệt đã dùng khi đăng ký, cho origin này.
            Map<String, String> vapid = new java.util.HashMap<>();
            for (String part : one.authorization().substring("vapid ".length()).split(",\\s*")) {
                vapid.put(part.substring(0, 1), part.substring(2));
            }
            assertThat(vapid.get("k")).isEqualTo(publicKey);
            Claims claims = Jwts.parser().verifyWith(WebPush.decodePublic(WebPush.unbase64Url(publicKey))).build()
                    .parseSignedClaims(vapid.get("t")).getPayload();
            assertThat(claims.getAudience()).containsExactly("http://127.0.0.1:" + service.getAddress().getPort());
            assertThat(claims.getSubject()).startsWith("mailto:");

            // EN: The push service says the browser is gone: NexBid forgets it. / VI: Dịch vụ push báo trình duyệt đã mất: NexBid quên nó.
            answer.set(410);
            notifications.notify(userId, NotificationType.AUCTION_WON, "You won", "Lot 001 is yours.", null,
                    Instant.now());
            await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.queryForObject(
                    "SELECT COUNT(*) FROM push_subscriptions WHERE user_id = ?", Integer.class, userId) == 0);
        } finally {
            service.stop(0);
        }
    }

    @Test
    void onlyKnownPushServicesAreAccepted() throws Exception {
        String bearer = register("push.strict@nexbid.com");
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = 'push.strict@nexbid.com'", UUID.class);
        PushService strict = new PushService(jdbc, null, objectMapper, "mailto:test@nexbid.test", false);
        String key = WebPush.base64Url(WebPush.encode((ECPublicKey) WebPush.newKeyPair().getPublic()));
        String auth = WebPush.base64Url(new byte[16]);
        for (String evil : List.of("http://127.0.0.1:8080/actuator", "https://169.254.169.254/latest",
                "https://fcm.googleapis.com.evil.test/x", "https://user@fcm.googleapis.com/x")) {
            assertThatThrownBy(() -> strict.subscribe(userId, new PushService.Subscription(evil, key, auth, "vi")))
                    .isInstanceOf(BusinessException.class);
        }
        strict.subscribe(userId, new PushService.Subscription("https://fcm.googleapis.com/fcm/send/abc", key, auth, "vi"));
        strict.subscribe(userId, new PushService.Subscription(
                "https://wns2-par02p.notify.windows.com/w/?token=abc", key, auth, "en"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_subscriptions WHERE user_id = ?", Integer.class,
                userId)).isEqualTo(2);
        mockMvc.perform(post("/api/users/me/push/unsubscribe").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/abc\"}"))
                .andExpect(jsonPath("$.data.subscribed").value(false));
        assertThat(push.publicKey()).isEqualTo(strict.publicKey());
    }

    /** EN: What the browser does with the body (RFC 8291 §3.4). / VI: Việc trình duyệt làm với body (RFC 8291 §3.4). */
    private static String decrypt(byte[] body, KeyPair browser, byte[] auth) throws Exception {
        ByteBuffer in = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        in.get(salt);
        assertThat(in.getInt()).isEqualTo(4096);
        byte[] senderKey = new byte[in.get()];
        in.get(senderKey);
        byte[] sealed = new byte[in.remaining()];
        in.get(sealed);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(browser.getPrivate());
        agreement.doPhase(WebPush.decodePublic(senderKey), true);
        byte[] shared = agreement.generateSecret();
        byte[] receiverKey = WebPush.encode((ECPublicKey) browser.getPublic());
        byte[] info = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[] {0}, receiverKey, senderKey,
                new byte[] {1});
        byte[] ikm = hmac(hmac(auth, shared), info);
        byte[] prk = hmac(salt, ikm);
        byte[] key = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII),
                new byte[] {0, 1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII),
                new byte[] {0, 1})), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = cipher.doFinal(sealed);
        assertThat(plain[plain.length - 1]).isEqualTo((byte) 2);
        return new String(plain, 0, plain.length - 1, StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        int length = Arrays.stream(parts).mapToInt(part -> part.length).sum();
        ByteBuffer out = ByteBuffer.allocate(length);
        for (byte[] part : parts) {
            out.put(part);
        }
        return out.array();
    }
}
