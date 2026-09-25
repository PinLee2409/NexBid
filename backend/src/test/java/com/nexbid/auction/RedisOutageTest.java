package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Redis dies mid-run (spec §20: "Redis is only a cache"). The site keeps working from the database, and
 *     only the first request after the failure pays a timeout. Its own properties give it its own context,
 *     so the Redis it stops is its own.
 * VI: Redis chết giữa chừng (spec §20: "Redis chỉ là cache"). Site vẫn chạy bằng database, và chỉ request
 *     đầu tiên sau sự cố phải chịu một lần timeout. Thuộc tính riêng cho nó context riêng, nên Redis bị tắt
 *     là Redis của riêng nó.
 */
@SpringBootTest(properties = { "nexbid.scheduler.enabled=false", "nexbid.cache.retry-after=60s" })
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
@ExtendWith(OutputCaptureExtension.class)
class RedisOutageTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("redis")
    private GenericContainer<?> redisContainer;

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

    /** EN: Runs a request and records how long it took. / VI: Chạy một request và ghi lại mất bao lâu. */
    private ResultActions timed(List<Long> millis, org.springframework.test.web.servlet.RequestBuilder request)
            throws Exception {
        long start = System.nanoTime();
        ResultActions result = mockMvc.perform(request);
        millis.add((System.nanoTime() - start) / 1_000_000);
        return result;
    }

    @Test
    void theSiteKeepsWorkingFromTheDatabaseWhenRedisDies(CapturedOutput output) throws Exception {
        String seller = tokenFor("outage.seller@nexbid.com", "Outage Seller", RoleName.SELLER);
        String admin = tokenFor("outage.admin@nexbid.com", "Outage Admin", RoleName.ADMIN);
        String buyer = tokenFor("outage.buyer@nexbid.com", "Outage Buyer", RoleName.BUYER);

        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Survivor","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(categoryId)))
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

        mockMvc.perform(get("/api/auctions/" + auction)).andExpect(status().isOk());

        redisContainer.stop();

        List<Long> millis = new ArrayList<>();
        timed(millis, get("/api/auctions/" + auction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product.name").value("Survivor"));
        timed(millis, get("/api/auctions?size=50")).andExpect(status().isOk());
        timed(millis, post("/api/auctions/" + auction + "/bids").header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":11000000}"))
                .andExpect(status().isCreated());
        timed(millis, get("/api/auctions/" + auction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auction.currentPrice").value(11000000));
        timed(millis, get("/api/auctions/" + auction)).andExpect(status().isOk());

        // EN: With Redis gone the bid limit fails open: a dozen quick bids from one person all go through,
        //     because refusing real bids over a broken guard would be worse than letting a burst in.
        // VI: Khi Redis mất, giới hạn trả giá cho qua: một người đặt liền cả chục lượt đều được nhận, vì chặn
        //     lượt trả giá thật chỉ vì lớp bảo vệ hỏng còn tệ hơn để lọt một đợt dồn dập.
        for (int i = 1; i <= 12; i++) {
            mockMvc.perform(post("/api/auctions/" + auction + "/bids").header("Authorization", "Bearer " + buyer)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":" + (11000000 + i * 500000L) + "}"))
                    .andExpect(status().isCreated());
        }

        // EN: Redis was tried once, failed, and then left alone for the rest of the run — five requests, one
        //     failure. Counted, not timed: how fast a refused connection fails varies from machine to machine.
        // VI: Redis được thử một lần, hỏng, rồi được để yên suốt phần còn lại — năm request, một lần hỏng. Đếm
        //     chứ không đo giờ: kết nối bị từ chối hỏng nhanh hay chậm tuỳ từng máy.
        assertThat(output.getOut().split("serving lots from the database", -1).length - 1).isEqualTo(1);
        // EN: The bid rate limiter has its own back-off: it failed open once and then stopped asking too.
        // VI: Bộ giới hạn tần suất có cơ chế lùi riêng: nó cho qua một lần rồi cũng thôi hỏi Redis.
        assertThat(output.getOut().split("bid rate limit not enforced", -1).length - 1).isEqualTo(1);
        assertThat(millis).allSatisfy(ms -> assertThat(ms).isLessThan(2_000));

        // EN: And the probe does not call the app down over a cache. / VI: Và probe không báo app sập chỉ vì cache.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
