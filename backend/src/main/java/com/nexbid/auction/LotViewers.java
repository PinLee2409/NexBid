package com.nexbid.auction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * EN: Counts who is on each lot's page (spec §20.3): one Redis set per lot, one member per socket
 *     subscription, kept in step with subscribe, unsubscribe and disconnect. Counts are announced on the
 *     lot's channel at most once a second, however fast people come and go.
 * VI: Đếm ai đang ở trang của từng lô (spec §20.3): mỗi lô một set Redis, mỗi lượt đăng ký socket một phần
 *     tử, cập nhật theo subscribe, unsubscribe và ngắt kết nối. Số được báo trên kênh của lô tối đa mỗi giây
 *     một lần, dù người vào ra nhanh tới đâu.
 */
@Component
class LotViewers {

    private static final Logger log = LoggerFactory.getLogger(LotViewers.class);

    private static final String PREFIX = "nexbid:viewers:";

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messages;
    private final Duration retryAfter;

    // EN: session → (subscription → lot): what an unsubscribe or a dropped connection has to undo.
    // VI: session → (subscription → lô): những gì một lần hủy đăng ký hay mất kết nối cần gỡ ra.
    private final Map<String, Map<String, UUID>> subscriptions = new ConcurrentHashMap<>();
    private final Set<UUID> changed = ConcurrentHashMap.newKeySet();

    /** EN: While in the future, Redis is not asked at all. / VI: Khi mốc này còn ở tương lai thì không hỏi Redis nữa. */
    private volatile Instant quietUntil = Instant.EPOCH;

    LotViewers(
            StringRedisTemplate redis,
            SimpMessagingTemplate messages,
            @Value("${nexbid.cache.retry-after}") Duration retryAfter) {

        this.redis = redis;
        this.messages = messages;
        this.retryAfter = retryAfter;
    }

    /**
     * EN: How many are watching, or null when Redis cannot say — the page then shows no count at all.
     * VI: Bao nhiêu người đang xem, hoặc null khi Redis không trả lời được — lúc đó trang không hiện số.
     */
    Integer countOf(UUID auctionId) {
        if (Instant.now().isBefore(quietUntil)) {
            return null;
        }
        try {
            Long size = redis.opsForSet().size(keyOf(auctionId));
            return size == null ? 0 : size.intValue();
        } catch (RuntimeException ex) {
            quiet(ex);
            return null;
        }
    }

    @EventListener
    void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        UUID auctionId = lotOf(headers.getDestination());
        if (auctionId == null) {
            return;
        }

        subscriptions.computeIfAbsent(headers.getSessionId(), session -> new ConcurrentHashMap<>())
                .put(headers.getSubscriptionId(), auctionId);
        change(auctionId, () -> redis.opsForSet().add(
                keyOf(auctionId), memberOf(headers.getSessionId(), headers.getSubscriptionId())));
    }

    @EventListener
    void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, UUID> mine = subscriptions.get(headers.getSessionId());
        UUID auctionId = mine == null ? null : mine.remove(headers.getSubscriptionId());
        if (auctionId != null) {
            leave(auctionId, headers.getSessionId(), headers.getSubscriptionId());
        }
    }

    @EventListener
    void onDisconnect(SessionDisconnectEvent event) {
        Map<String, UUID> mine = subscriptions.remove(event.getSessionId());
        if (mine != null) {
            mine.forEach((subscription, auctionId) -> leave(auctionId, event.getSessionId(), subscription));
        }
    }

    /**
     * EN: Sockets live in this process (the broker is in memory), so whatever a previous run left in Redis
     *     belongs to connections that no longer exist.
     * VI: Socket nằm trong tiến trình này (broker ở trong bộ nhớ), nên những gì lần chạy trước để lại trong
     *     Redis thuộc về các kết nối không còn tồn tại.
     */
    @EventListener(ApplicationReadyEvent.class)
    void forgetPreviousRun() {
        try {
            List<String> stale = new ArrayList<>();
            try (Cursor<String> keys = redis.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(500).build())) {
                keys.forEachRemaining(stale::add);
            }
            if (!stale.isEmpty()) {
                redis.delete(stale);
            }
        } catch (RuntimeException ex) {
            quiet(ex);
        }
    }

    /** EN: Announces the lots whose count moved since the last tick. / VI: Báo các lô có số người xem thay đổi kể từ nhịp trước. */
    @Scheduled(fixedDelayString = "${nexbid.realtime.viewer-count-interval:1000}")
    void announce() {
        for (UUID auctionId : List.copyOf(changed)) {
            changed.remove(auctionId);
            Integer count = countOf(auctionId);
            if (count != null) {
                messages.convertAndSend(AuctionChannel.topicFor(auctionId),
                        new ViewerCountMessage(ViewerCountMessage.TYPE, auctionId, count));
            }
        }
    }

    private void leave(UUID auctionId, String sessionId, String subscriptionId) {
        change(auctionId, () -> redis.opsForSet().remove(keyOf(auctionId), memberOf(sessionId, subscriptionId)));
    }

    /** EN: A Redis failure costs the count, never the subscription. / VI: Redis lỗi chỉ làm mất số đếm, không bao giờ làm mất lượt đăng ký. */
    private void change(UUID auctionId, Runnable update) {
        if (Instant.now().isBefore(quietUntil)) {
            return;
        }
        try {
            update.run();
            changed.add(auctionId);
        } catch (RuntimeException ex) {
            quiet(ex);
        }
    }

    private void quiet(RuntimeException ex) {
        quietUntil = Instant.now().plus(retryAfter);
        log.warn("Viewer counts are unavailable for {}: {}", retryAfter, ex.getMessage());
    }

    private static UUID lotOf(String destination) {
        if (destination == null || !destination.startsWith(AuctionChannel.TOPIC_PREFIX)) {
            return null;
        }
        try {
            return UUID.fromString(destination.substring(AuctionChannel.TOPIC_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String keyOf(UUID auctionId) {
        return PREFIX + auctionId;
    }

    private static String memberOf(String sessionId, String subscriptionId) {
        return sessionId + ":" + subscriptionId;
    }
}
