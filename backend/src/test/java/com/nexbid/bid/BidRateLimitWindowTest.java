package com.nexbid.bid;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.nexbid.common.exception.RateLimitedException;
import com.nexbid.support.TestInfrastructure;

/**
 * EN: The window slides (spec §20.2 says "per 10 seconds", not "per clock tick of 10 seconds"). Shrunk to
 *     3 per second here so the test does not wait ten seconds to prove it.
 * VI: Khung thời gian là khung trượt (spec §20.2 nói "mỗi 10 giây", không phải "mỗi khung 10 giây theo
 *     đồng hồ"). Ở đây thu nhỏ còn 3 lượt mỗi giây để test không phải chờ mười giây mới chứng minh được.
 */
@SpringBootTest(properties = {
        "nexbid.scheduler.enabled=false",
        "nexbid.rate-limit.bids.limit=3",
        "nexbid.rate-limit.bids.window=1s"
})
@Import(TestInfrastructure.class)
class BidRateLimitWindowTest {

    @Autowired
    private BidRateLimiter limiter;

    @Autowired
    private StringRedisTemplate redis;

    private long redisMillis() {
        return redis.execute((RedisCallback<Long>) connection ->
                connection.serverCommands().time(TimeUnit.MILLISECONDS));
    }

    @Test
    void theWindowSlidesRatherThanResettingOnTheClock() throws Exception {
        UUID someone = UUID.randomUUID();

        // EN: Start the burst late in a second of Redis's clock, so a whole second ticks over shortly after.
        // VI: Bắt đầu đợt request ở cuối một giây theo đồng hồ Redis, để ngay sau đó sang giây mới.
        long intoSecond = redisMillis() % 1000;
        while (intoSecond < 700 || intoSecond > 750) {
            Thread.sleep(5);
            intoSecond = redisMillis() % 1000;
        }

        for (int i = 0; i < 3; i++) {
            limiter.check(someone);
        }
        assertThatThrownBy(() -> limiter.check(someone)).isInstanceOf(RateLimitedException.class);

        // EN: 600 ms later the second has ticked over, which would reset a fixed window; a sliding one still
        //     sees all three requests inside the last second.
        // VI: 600 ms sau đã sang giây mới, khung cố định sẽ bị reset; khung trượt thì vẫn thấy đủ ba request
        //     nằm trong một giây vừa qua.
        Thread.sleep(600);
        assertThatThrownBy(() -> limiter.check(someone)).isInstanceOf(RateLimitedException.class);

        // EN: Once the first three are more than a second old, there is room again.
        // VI: Khi ba request đầu đã cũ hơn một giây thì lại có chỗ.
        Thread.sleep(500);
        assertThatCode(() -> limiter.check(someone)).doesNotThrowAnyException();
    }
}
