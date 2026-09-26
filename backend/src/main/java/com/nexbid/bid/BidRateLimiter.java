package com.nexbid.bid;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.RateLimitedException;

/**
 * EN: At most N bid requests per person in any sliding window (guide §35, spec §20.2), counted in Redis so
 *     every app instance shares one count. Redis being down lets bids through: this is a guard, not a rule.
 * VI: Tối đa N request trả giá mỗi người trong bất kỳ khung trượt nào (guide §35, spec §20.2), đếm trong
 *     Redis để mọi instance dùng chung một con số. Redis sập thì cho qua: đây là lớp bảo vệ, không phải luật.
 */
@Component
class BidRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(BidRateLimiter.class);

    /**
     * EN: One atomic step: drop entries older than the window, and let this one in only if there is room.
     *     Uses Redis's own clock so instances with drifting clocks still agree. Returns 0 when allowed,
     *     otherwise the milliseconds until the oldest entry leaves the window.
     * VI: Một bước nguyên tử: bỏ các mục cũ hơn khung, rồi chỉ cho lượt này vào nếu còn chỗ. Dùng đồng hồ của
     *     chính Redis nên các instance lệch giờ vẫn thống nhất. Trả 0 nếu được phép, ngược lại là số mili giây
     *     tới khi mục cũ nhất rời khỏi khung.
     */
    private static final RedisScript<Long> SLIDING_WINDOW = RedisScript.of("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - window)
            if redis.call('ZCARD', KEYS[1]) < limit then
                redis.call('ZADD', KEYS[1], now, ARGV[3])
                redis.call('PEXPIRE', KEYS[1], window)
                return 0
            end
            local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
            return math.max(1, tonumber(oldest[2]) + window - now)
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int limit;
    private final Duration window;
    private final Duration retryAfter;

    private volatile Instant quietUntil = Instant.EPOCH;

    BidRateLimiter(
            StringRedisTemplate redis,
            @Value("${nexbid.rate-limit.bids.limit}") int limit,
            @Value("${nexbid.rate-limit.bids.window}") Duration window,
            @Value("${nexbid.cache.retry-after}") Duration retryAfter) {

        this.redis = redis;
        this.limit = limit;
        this.window = window;
        this.retryAfter = retryAfter;
    }

    /** EN: Guide §35 names the key. / VI: Guide §35 đặt tên cho khoá này. */
    static String keyOf(UUID userId) {
        return "rate:bid:" + userId;
    }

    /**
     * EN: Counts this request and refuses it with 429 if the person is over the limit.
     * VI: Đếm request này và từ chối bằng 429 nếu người đó đã vượt giới hạn.
     */
    void check(UUID userId) {
        if (Instant.now().isBefore(quietUntil)) {
            return;
        }

        Long waitMillis;
        try {
            waitMillis = redis.execute(SLIDING_WINDOW, List.of(keyOf(userId)),
                    Long.toString(window.toMillis()), Integer.toString(limit), UUID.randomUUID().toString());
        } catch (RuntimeException ex) {
            // EN: Fail open, and stop asking for a while. / VI: Cho qua, và thôi hỏi một lúc.
            quietUntil = Instant.now().plus(retryAfter);
            log.warn("Redis unavailable, bid rate limit not enforced for {}: {}", retryAfter, ex.getMessage());
            return;
        }

        if (waitMillis != null && waitMillis > 0) {
            throw new RateLimitedException(
                    ErrorCode.BID_RATE_LIMITED,
                    "Too many bids; at most " + limit + " every " + window.toSeconds() + " seconds",
                    Duration.ofMillis(waitMillis));
        }
    }
}
