package com.nexbid.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nexbid.common.exception.RateLimitedException;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The bid rate limit (guide §35, spec §20.2): 10 bid requests per 10 seconds per person.
 * VI: Giới hạn tần suất trả giá (guide §35, spec §20.2): 10 request trả giá mỗi 10 giây mỗi người.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class BidRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BidRateLimiter limiter;

    @Autowired
    private AutoBidService autoBids;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ObjectMapper objectMapper;

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

    private String openLot(String tag) throws Exception {
        String seller = tokenFor("rl.seller." + tag + "@nexbid.com", "Seller " + tag, RoleName.SELLER);
        String admin = tokenFor("rl.admin." + tag + "@nexbid.com", "Admin " + tag, RoleName.ADMIN);
        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Lot %s","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(tag, categoryId)))
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();
        String auctionBody = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();
        String auction = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auction + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auction);
        return auction;
    }

    private ResultActions bid(String token, String auction, long amount) throws Exception {
        return mockMvc.perform(post("/api/auctions/" + auction + "/bids").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + amount + "}"));
    }

    @Test
    void tenBidsGoThroughAndTheEleventhIsRefusedWithAWaitingTime() throws Exception {
        String auction = openLot("ten");
        String buyer = tokenFor("rl.buyer.ten@nexbid.com", "Ten Buyer", RoleName.BUYER);

        for (int i = 0; i < 10; i++) {
            bid(buyer, auction, 10_000_000L + i * 500_000L).andExpect(status().isCreated());
        }

        String retryAfter = bid(buyer, auction, 20_000_000L)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BID_RATE_LIMITED"))
                .andExpect(header().exists("Retry-After"))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Integer.parseInt(retryAfter)).isBetween(1, 10);

        // EN: Guide §35's key, holding exactly the ten counted requests, and gone once the window passes.
        // VI: Đúng khoá của guide §35, giữ đúng mười request đã đếm, và tự biến mất khi hết khung.
        String key = BidRateLimiter.keyOf(idOf(buyer));
        assertThat(redis.opsForZSet().zCard(key)).isEqualTo(10);
        assertThat(redis.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1L, 10_000L);
    }

    @Test
    void aThrottledRequestChangesNothing() throws Exception {
        String auction = openLot("nothing");
        String buyer = tokenFor("rl.buyer.nothing@nexbid.com", "Nothing Buyer", RoleName.BUYER);
        for (int i = 0; i < 10; i++) {
            bid(buyer, auction, 10_000_000L + i * 500_000L);
        }

        bid(buyer, auction, 30_000_000L).andExpect(status().isTooManyRequests());

        assertThat(jdbc.queryForObject("SELECT current_price FROM auctions WHERE id = ?::uuid",
                java.math.BigDecimal.class, auction)).isEqualByComparingTo("14500000");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bids WHERE auction_id = ?::uuid",
                Integer.class, auction)).isEqualTo(10);
    }

    @Test
    void refusedBidsCountToo() throws Exception {
        String auction = openLot("refused");
        String buyer = tokenFor("rl.buyer.refused@nexbid.com", "Refused Buyer", RoleName.BUYER);

        // EN: Ten bids below the opening price — every one refused, every one counted. This limits spam.
        // VI: Mười lượt dưới giá khởi điểm — lượt nào cũng bị từ chối, lượt nào cũng bị đếm. Đây là chống spam.
        for (int i = 0; i < 10; i++) {
            bid(buyer, auction, 5_000_000L).andExpect(status().isUnprocessableEntity());
        }

        bid(buyer, auction, 10_000_000L).andExpect(status().isTooManyRequests());
    }

    @Test
    void onePersonsLimitIsTheirOwn() throws Exception {
        String auction = openLot("own");
        String busy = tokenFor("rl.busy@nexbid.com", "Busy Buyer", RoleName.BUYER);
        String calm = tokenFor("rl.calm@nexbid.com", "Calm Buyer", RoleName.BUYER);

        for (int i = 0; i < 10; i++) {
            bid(busy, auction, 10_000_000L + i * 500_000L);
        }
        bid(busy, auction, 20_000_000L).andExpect(status().isTooManyRequests());

        bid(calm, auction, 20_000_000L).andExpect(status().isCreated());
    }

    @Test
    void autoBidAnswersAreNotCountedAgainstAnyone() throws Exception {
        String auction = openLot("proxy");
        String owner = tokenFor("rl.proxy.owner@nexbid.com", "Proxy Owner", RoleName.BUYER);
        List<String> rivals = List.of(
                tokenFor("rl.proxy.r1@nexbid.com", "Rival One", RoleName.BUYER),
                tokenFor("rl.proxy.r2@nexbid.com", "Rival Two", RoleName.BUYER),
                tokenFor("rl.proxy.r3@nexbid.com", "Rival Three", RoleName.BUYER));
        UUID ownerId = idOf(owner);

        autoBids.create(ownerId, UUID.fromString(auction), new java.math.BigDecimal("999000000"));

        // EN: Fifteen manual bids; the owner gets an opening bid plus fifteen answers without sending a bid request.
        // VI: Mười lăm lượt trả tay; chủ auto bid có một lượt mở đầu cộng mười lăm lần đáp trả mà không gửi request trả giá nào.
        long amount = 10_500_000L;
        for (int i = 0; i < 15; i++) {
            bid(rivals.get(i % 3), auction, amount).andExpect(status().isCreated());
            amount += 1_000_000L;
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM bids WHERE auction_id = ?::uuid AND bidder_id = ?",
                Integer.class, auction, ownerId)).isEqualTo(16);
        assertThat(redis.hasKey(BidRateLimiter.keyOf(ownerId))).isFalse();
    }

    @Test
    void underConcurrentFireExactlyTheLimitGetsThrough() throws Exception {
        UUID someone = UUID.randomUUID();
        AtomicInteger allowed = new AtomicInteger();
        List<Throwable> other = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(40);

        // EN: Forty requests from one person at the same instant. A read-then-write count would let more
        //     than ten slip through; the script is one atomic step, so it cannot.
        // VI: Bốn mươi request của cùng một người tại cùng một lúc. Kiểu đếm "đọc rồi mới ghi" sẽ để lọt hơn
        //     mười; script là một bước nguyên tử nên không thể.
        try (ExecutorService pool = Executors.newFixedThreadPool(40)) {
            for (int i = 0; i < 40; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        limiter.check(someone);
                        allowed.incrementAndGet();
                    } catch (RateLimitedException ex) {
                        // EN: Expected for thirty of them. / VI: Ba mươi request sẽ rơi vào đây.
                    } catch (Throwable ex) {
                        other.add(ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(other).isEmpty();
        assertThat(allowed.get()).isEqualTo(10);
    }
}
