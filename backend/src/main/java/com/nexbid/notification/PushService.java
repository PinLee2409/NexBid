package com.nexbid.notification;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nexbid.auction.AuctionService;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;

import io.jsonwebtoken.Jwts;
import tools.jackson.databind.ObjectMapper;

/**
 * EN: Browser push (Web Push with VAPID): every notice that rings the bell is also sent to the browsers its owner allowed,
 *     so it arrives with the tab closed. The key pair is made on first use and kept in the database. Push services are
 *     only the known ones (Google, Mozilla, Microsoft, Apple), since the endpoint comes from the browser and a server
 *     that posts wherever it is told is a server others can aim. A subscription the service says is gone is deleted.
 * VI: Thông báo đẩy trên trình duyệt (Web Push với VAPID): mỗi thông báo rung chuông cũng được gửi tới các trình duyệt chủ
 *     nhân đã cho phép, nên tới được cả khi đã đóng tab. Cặp khoá được tạo lần đầu dùng và giữ trong database. Chỉ gửi tới
 *     các dịch vụ push đã biết (Google, Mozilla, Microsoft, Apple), vì endpoint do trình duyệt gửi lên và một server gửi
 *     request tới bất cứ đâu được bảo là server người khác nhắm vào được. Đăng ký mà dịch vụ báo đã mất thì bị xoá.
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);
    private static final Set<String> KNOWN_SERVICES = Set.of("fcm.googleapis.com", "updates.push.services.mozilla.com",
            "push.services.mozilla.com", "notify.windows.com", "push.apple.com");
    private static final int MAX_PER_USER = 10;
    private static final Map<String, String> VIETNAMESE = Map.ofEntries(
            Map.entry("OUTBID", "Bị vượt giá"),
            Map.entry("AUCTION_STARTING", "Phiên sắp bắt đầu"),
            Map.entry("AUCTION_ENDING", "Phiên sắp kết thúc"),
            Map.entry("AUCTION_WON", "Thắng phiên đấu giá"),
            Map.entry("AUCTION_LOST", "Không thắng"),
            Map.entry("AUCTION_EXTENDED", "Phiên được kéo dài"),
            Map.entry("PAYMENT_REQUIRED", "Cần thanh toán"),
            Map.entry("PAYMENT_SUCCESS", "Thanh toán thành công"),
            Map.entry("PAYMENT_EXPIRED", "Thanh toán hết hạn"),
            Map.entry("AUCTION_CANCELLED", "Phiên bị huỷ"),
            Map.entry("SELLER_APPROVED", "Được duyệt bán hàng"),
            Map.entry("SELLER_REJECTED", "Yêu cầu bán hàng bị từ chối"),
            Map.entry("SECOND_CHANCE_OFFER", "Đề nghị cơ hội thứ hai"),
            Map.entry("SECOND_CHANCE_ACCEPTED", "Đề nghị được nhận"),
            Map.entry("SECOND_CHANCE_DECLINED", "Đề nghị bị từ chối"),
            Map.entry("NEW_REVIEW", "Đánh giá mới"),
            Map.entry("RESERVE_NOT_MET", "Chưa đạt giá sàn"),
            Map.entry("DISPUTE_OPENED", "Có khiếu nại mới"),
            Map.entry("DISPUTE_RESOLVED", "Khiếu nại đã có kết quả"),
            Map.entry("DISPUTE_SELLER_RESOLVED", "Khiếu nại đã có kết quả"),
            Map.entry("PAYOUT_SENT", "Đã chuyển tiền"),
            Map.entry("ORDER_COMPLETING", "Đơn sắp tự hoàn tất"),
            Map.entry("ORDER_REFUNDED", "Đã chuyển tiền hoàn"),
            Map.entry("NEW_QUESTION", "Câu hỏi mới"),
            Map.entry("QUESTION_ANSWERED", "Đã có câu trả lời"),
            Map.entry("LOT_ALERT", "Lô mới cho bạn"),
            Map.entry("STRAY_CHARGE", "Khoản thu thừa"),
            Map.entry("SALE_REFUNDED", "Giao dịch đã hoàn tiền"),
            Map.entry("LOT_REMOVED", "Lô bị gỡ"),
            Map.entry("REPORT_RESOLVED", "Báo cáo đã xử lý"),
            Map.entry("SELLER_VERIFIED", "Người bán đã xác minh"),
            Map.entry("NEW_MESSAGE", "Tin nhắn mới"),
            Map.entry("SHIPMENT_CANCELLED", "Đã huỷ vận đơn"),
            Map.entry("ORDER_ADDRESS_CHANGED", "Đổi địa chỉ nhận"),
            Map.entry("OFFER_RECEIVED", "Có đề nghị mua mới"),
            Map.entry("OFFER_ANSWERED", "Đề nghị đã có trả lời"),
            Map.entry("LOT_RELISTED", "Lô được đăng lại rẻ hơn"),
            Map.entry("MEDIA_REMOVED", "Ảnh hoặc video bị gỡ"));

    private final JdbcTemplate jdbc;
    private final AuctionService auctions;
    private final ObjectMapper json;
    private final String subject;
    private final boolean allowAnyHost;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService senders = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "web-push");
        thread.setDaemon(true);
        return thread;
    });
    private volatile KeyPair keys;

    PushService(JdbcTemplate jdbc, AuctionService auctions, ObjectMapper json,
            @Value("${nexbid.push.subject:mailto:${nexbid.mail.sender-email:no-reply@nexbid.test}}") String subject,
            @Value("${nexbid.push.allow-any-host:false}") boolean allowAnyHost) {
        this.jdbc = jdbc;
        this.auctions = auctions;
        this.json = json;
        this.subject = subject;
        this.allowAnyHost = allowAnyHost;
    }

    /** EN: The application server key browsers subscribe with. / VI: Khoá application server mà trình duyệt dùng để đăng ký. */
    public String publicKey() {
        return WebPush.base64Url(WebPush.encode((ECPublicKey) keys().getPublic()));
    }

    /** EN: What the browser's PushSubscription.toJSON() holds. / VI: Những gì PushSubscription.toJSON() của trình duyệt chứa. */
    public record Subscription(String endpoint, String p256dh, String auth, String locale) {
    }

    /**
     * EN: Remembers a browser for a user. The same endpoint moves to whoever subscribed last on that browser.
     * VI: Ghi nhớ một trình duyệt cho một người dùng. Cùng endpoint thì thuộc về người đăng ký sau cùng trên trình duyệt đó.
     */
    public void subscribe(UUID userId, Subscription subscription) {
        URI endpoint = checkedEndpoint(subscription.endpoint());
        byte[] key;
        byte[] auth;
        try {
            key = WebPush.unbase64Url(subscription.p256dh());
            auth = WebPush.unbase64Url(subscription.auth());
            WebPush.decodePublic(key);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PUSH_SUBSCRIPTION_INVALID, "The browser's push keys cannot be read");
        }
        if (auth.length != 16) {
            throw new BusinessException(ErrorCode.PUSH_SUBSCRIPTION_INVALID, "The browser's push keys cannot be read");
        }
        String locale = "en".equals(subscription.locale()) ? "en" : "vi";
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO push_subscriptions (id, user_id, endpoint, p256dh, auth, locale, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (endpoint) DO UPDATE SET user_id = EXCLUDED.user_id, p256dh = EXCLUDED.p256dh,
                    auth = EXCLUDED.auth, locale = EXCLUDED.locale
                """, UUID.randomUUID(), userId, endpoint.toString(), WebPush.base64Url(key), WebPush.base64Url(auth),
                locale, now.atOffset(ZoneOffset.UTC));
        // EN: Old browsers beyond the newest ten are forgotten. / VI: Trình duyệt cũ ngoài mười cái mới nhất bị quên.
        jdbc.update("""
                DELETE FROM push_subscriptions WHERE user_id = ? AND id NOT IN (
                    SELECT id FROM push_subscriptions WHERE user_id = ? ORDER BY created_at DESC LIMIT ?)
                """, userId, userId, MAX_PER_USER);
    }

    public void unsubscribe(UUID userId, String endpoint) {
        jdbc.update("DELETE FROM push_subscriptions WHERE user_id = ? AND endpoint = ?", userId, endpoint);
    }

    public boolean subscribed(UUID userId, String endpoint) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM push_subscriptions WHERE user_id = ? AND endpoint = ?)",
                Boolean.class, userId, endpoint));
    }

    /** EN: After commit, off the request thread. / VI: Sau khi commit, ngoài luồng của request. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCreated(NotificationCreated event) {
        senders.execute(() -> {
            try {
                send(event.userId(), event.notification());
            } catch (RuntimeException ex) {
                log.warn("Push for notice {} failed: {}", event.notification().id(), ex.getMessage());
            }
        });
    }

    private record Target(UUID id, String endpoint, String p256dh, String auth, String locale) {
    }

    /** EN: Sends one notice to every browser of its owner; returns how many accepted it. / VI: Gửi một thông báo tới mọi trình duyệt của chủ nhân; trả về số trình duyệt đã nhận. */
    int send(UUID userId, NotificationView notice) {
        List<Target> targets = jdbc.query(
                "SELECT id, endpoint, p256dh, auth, locale FROM push_subscriptions WHERE user_id = ?",
                (rs, n) -> new Target(rs.getObject("id", UUID.class), rs.getString("endpoint"), rs.getString("p256dh"),
                        rs.getString("auth"), rs.getString("locale")), userId);
        if (targets.isEmpty()) {
            return 0;
        }
        String lot = notice.auctionId() == null ? null : auctions.lotTitleOf(notice.auctionId());
        int delivered = 0;
        for (Target target : targets) {
            if (deliver(target, payload(notice, lot, target.locale()))) {
                delivered++;
            }
        }
        return delivered;
    }

    private byte[] payload(NotificationView notice, String lot, String locale) {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean vietnamese = "vi".equals(locale);
        body.put("title", vietnamese ? VIETNAMESE.getOrDefault(notice.type().name(), "NexBid") : notice.title());
        body.put("body", vietnamese
                ? (lot != null ? lot : "Mở NexBid để xem chi tiết.")
                : notice.message());
        body.put("url", notice.auctionId() != null ? "/auctions/" + notice.auctionId() : "/notifications");
        body.put("tag", notice.type().name() + ":" + (notice.auctionId() == null ? notice.id() : notice.auctionId()));
        body.put("type", notice.type().name());
        return json.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
    }

    private boolean deliver(Target target, byte[] payload) {
        URI endpoint;
        try {
            endpoint = checkedEndpoint(target.endpoint());
        } catch (BusinessException ex) {
            forget(target.id());
            return false;
        }
        byte[] body = WebPush.encrypt(payload, WebPush.unbase64Url(target.p256dh()), WebPush.unbase64Url(target.auth()));
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Encoding", "aes128gcm")
                .header("Content-Type", "application/octet-stream")
                .header("TTL", "86400")
                .header("Urgency", "high")
                .header("Authorization", "vapid t=" + vapidToken(endpoint) + ", k=" + publicKey())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            int status = http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404 || status == 410) {
                forget(target.id());
                return false;
            }
            if (status >= 200 && status < 300) {
                jdbc.update("UPDATE push_subscriptions SET last_sent_at = ? WHERE id = ?",
                        Instant.now().atOffset(ZoneOffset.UTC), target.id());
                return true;
            }
            log.warn("Push service {} answered {}", endpoint.getHost(), status);
            return false;
        } catch (java.io.IOException ex) {
            log.warn("Push service {} unreachable: {}", endpoint.getHost(), ex.getMessage());
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void forget(UUID subscriptionId) {
        jdbc.update("DELETE FROM push_subscriptions WHERE id = ?", subscriptionId);
    }

    /** EN: RFC 8292: signed for the push service's origin, good for twelve hours. / VI: RFC 8292: ký cho origin của dịch vụ push, hiệu lực mười hai giờ. */
    private String vapidToken(URI endpoint) {
        Instant now = Instant.now();
        String audience = endpoint.getScheme() + "://" + endpoint.getHost()
                + (endpoint.getPort() == -1 ? "" : ":" + endpoint.getPort());
        return Jwts.builder().header().add("typ", "JWT").and()
                .audience().add(audience).and()
                .subject(subject)
                .expiration(Date.from(now.plus(Duration.ofHours(12))))
                .signWith((ECPrivateKey) keys().getPrivate(), Jwts.SIG.ES256)
                .compact();
    }

    private URI checkedEndpoint(String raw) {
        URI endpoint;
        try {
            endpoint = URI.create(raw == null ? "" : raw.trim());
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
        String host = endpoint.getHost() == null ? "" : endpoint.getHost().toLowerCase(Locale.ROOT);
        if (allowAnyHost) {
            if (host.isEmpty() || !List.of("http", "https").contains(endpoint.getScheme())) {
                throw invalid();
            }
            return endpoint;
        }
        boolean known = KNOWN_SERVICES.stream().anyMatch(service -> host.equals(service) || host.endsWith("." + service));
        if (!"https".equals(endpoint.getScheme()) || !known || endpoint.getUserInfo() != null) {
            throw invalid();
        }
        return endpoint;
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.PUSH_SUBSCRIPTION_INVALID, "That is not a known browser push service");
    }

    /** EN: Loaded once, made on first use. / VI: Nạp một lần, tạo ra khi dùng lần đầu. */
    private KeyPair keys() {
        KeyPair found = keys;
        if (found != null) {
            return found;
        }
        synchronized (this) {
            if (keys == null) {
                KeyPair fresh = WebPush.newKeyPair();
                jdbc.update("""
                        INSERT INTO push_keys (id, public_key, private_key, created_at) VALUES (1, ?, ?, ?)
                        ON CONFLICT (id) DO NOTHING
                        """, WebPush.base64Url(WebPush.encode((ECPublicKey) fresh.getPublic())),
                        WebPush.base64Url(fresh.getPrivate().getEncoded()), Instant.now().atOffset(ZoneOffset.UTC));
                keys = jdbc.queryForObject("SELECT public_key, private_key FROM push_keys WHERE id = 1",
                        (rs, n) -> new KeyPair(WebPush.decodePublic(WebPush.unbase64Url(rs.getString(1))),
                                WebPush.decodePrivate(WebPush.unbase64Url(rs.getString(2)))));
            }
            return keys;
        }
    }
}
