package com.nexbid.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
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
 * EN: Slows down password guessing. Wrong passwords are counted twice over a sliding window: per email and
 *     address together (a few tries, so a typo is forgiven but guessing is not), and per address alone (more,
 *     so one address cannot try many accounts). Keying the first by address too means nobody can lock another
 *     person out of their account from elsewhere. A correct password clears its pair. Counted in Redis so every
 *     instance shares one count; Redis being down lets sign-ins through, like the bid limit.
 * VI: Làm chậm việc dò mật khẩu. Mật khẩu sai được đếm hai lần trong một khung thời gian trượt: theo email cùng
 *     địa chỉ (vài lần, đủ để gõ nhầm nhưng không đủ để dò), và theo riêng địa chỉ (nhiều hơn, để một địa chỉ
 *     không thử được hàng loạt tài khoản). Khoá đầu có cả địa chỉ nên không ai khoá được tài khoản của người khác
 *     từ nơi khác. Mật khẩu đúng thì xoá bộ đếm của cặp đó. Đếm trong Redis để mọi instance dùng chung; Redis sập
 *     thì cho đăng nhập qua, giống giới hạn trả giá.
 */
@Component
class LoginAttempts {

    private static final Logger log = LoggerFactory.getLogger(LoginAttempts.class);

    /**
     * EN: Milliseconds until the caller may try again, or 0. Trims each window first, using Redis's own clock.
     * VI: Số mili giây tới khi được thử lại, hoặc 0. Cắt bớt từng khung trước, dùng đồng hồ của chính Redis.
     */
    private static final RedisScript<Long> WAIT = RedisScript.of("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local window = tonumber(ARGV[1])
            local wait = 0
            for i, key in ipairs(KEYS) do
                redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window)
                if redis.call('ZCARD', key) >= tonumber(ARGV[i + 1]) then
                    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                    wait = math.max(wait, tonumber(oldest[2]) + window - now, 1)
                end
            end
            return wait
            """, Long.class);

    private static final RedisScript<Long> RECORD = RedisScript.of("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            for i, key in ipairs(KEYS) do
                redis.call('ZADD', key, now, ARGV[2] .. ':' .. i)
                redis.call('PEXPIRE', key, tonumber(ARGV[1]))
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int accountLimit;
    private final int addressLimit;
    private final Duration window;
    private final Duration retryAfter;

    private volatile Instant quietUntil = Instant.EPOCH;

    LoginAttempts(
            StringRedisTemplate redis,
            @Value("${nexbid.rate-limit.login.account-limit}") int accountLimit,
            @Value("${nexbid.rate-limit.login.address-limit}") int addressLimit,
            @Value("${nexbid.rate-limit.login.window}") Duration window,
            @Value("${nexbid.cache.retry-after}") Duration retryAfter) {

        this.redis = redis;
        this.accountLimit = accountLimit;
        this.addressLimit = addressLimit;
        this.window = window;
        this.retryAfter = retryAfter;
    }

    /** EN: Refuses with 429 before the password is even checked. / VI: Từ chối bằng 429 trước cả khi kiểm mật khẩu. */
    void checkAllowed(String email, String address) {
        Long waitMillis = run(WAIT, email, address, Integer.toString(accountLimit), Integer.toString(addressLimit));
        if (waitMillis != null && waitMillis > 0) {
            throw new RateLimitedException(
                    ErrorCode.LOGIN_RATE_LIMITED,
                    "Too many failed sign-ins; try again later",
                    Duration.ofMillis(waitMillis));
        }
    }

    void recordFailure(String email, String address) {
        run(RECORD, email, address, UUID.randomUUID().toString());
    }

    /** EN: A correct password forgives the typos before it. / VI: Mật khẩu đúng thì bỏ qua các lần gõ nhầm trước đó. */
    void clear(String email, String address) {
        try {
            redis.delete(accountKey(email, address));
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable, failed sign-ins not cleared: {}", ex.getMessage());
        }
    }

    private Long run(RedisScript<Long> script, String email, String address, String... extra) {
        if (Instant.now().isBefore(quietUntil)) {
            return 0L;
        }
        String[] args = new String[extra.length + 1];
        args[0] = Long.toString(window.toMillis());
        System.arraycopy(extra, 0, args, 1, extra.length);
        try {
            return redis.execute(script, List.of(accountKey(email, address), addressKey(address)), (Object[]) args);
        } catch (RuntimeException ex) {
            // EN: Fail open, and stop asking for a while. / VI: Cho qua, và thôi hỏi một lúc.
            quietUntil = Instant.now().plus(retryAfter);
            log.warn("Redis unavailable, sign-in limit not enforced for {}: {}", retryAfter, ex.getMessage());
            return 0L;
        }
    }

    /** EN: Hashed, so no email address sits in Redis in the clear. / VI: Được băm, để không có địa chỉ email nào nằm trần trong Redis. */
    static String accountKey(String email, String address) {
        return "rate:login:account:" + sha256(email.trim().toLowerCase(Locale.ROOT) + "|" + address);
    }

    static String addressKey(String address) {
        return "rate:login:address:" + address;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", ex);
        }
    }
}
