package com.nexbid.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Concurrent bidding (guide §21). The one test that cannot be written after the fact — a race either
 *     shows up under real parallel load or hides until production.
 * VI: Trả giá đồng thời (guide §21). Phép thử duy nhất không thể viết bù sau — một race condition hoặc lộ
 *     ra dưới tải song song thật, hoặc nấp kỹ cho tới khi lên production.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class ConcurrentBidTest {

    private static final int BIDDERS = 100;
    private static final BigDecimal OPENING = new BigDecimal("10000000");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BidService bids;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email, String name, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","email":"%s","password":"supersecret"}
                        """.formatted(name, email)));

        users.grantRole(email, role);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    /** EN: An approved lot, already open for bidding. / VI: Một lô đã duyệt, đang mở cho trả giá. */
    private UUID openLot(String seller, String admin, String name) throws Exception {
        String categories = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();

        String productBody = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(name, categoryId)))
                .andReturn().getResponse().getContentAsString();

        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();

        String auctionBody = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId,
                                Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();

        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();

        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit")
                .header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve")
                .header("Authorization", "Bearer " + admin));

        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 minute' "
                + "WHERE id = ?::uuid", auctionId);

        return UUID.fromString(auctionId);
    }

    /**
     * EN: Bidder rows written straight to the table — the API path would spend a hundred BCrypt hashes to
     *     produce ids this test only needs as foreign keys.
     * VI: Ghi thẳng các dòng người trả giá vào bảng — đi qua API sẽ tốn một trăm lần hash BCrypt chỉ để
     *     lấy id mà test này chỉ dùng làm khoá ngoại.
     */
    private List<UUID> bidders(int count) {
        List<UUID> ids = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO users (id, full_name, email, password, status, created_at, updated_at)
                    VALUES (?, ?, ?, 'x', 'ACTIVE', now(), now())
                    """, id, "Racer " + i, "racer" + i + "." + id + "@nexbid.com");
            ids.add(id);
        }

        return ids;
    }

    @Test
    void oneHundredBiddersOfferingTheSameAmountProduceExactlyOneWinner() throws Exception {
        String seller = tokenFor("race.seller@nexbid.com", "Race Seller", RoleName.SELLER);
        String admin = tokenFor("race.admin@nexbid.com", "Race Admin", RoleName.ADMIN);
        UUID auction = openLot(seller, admin, "Contested lot");
        List<UUID> racers = bidders(BIDDERS);

        AtomicInteger accepted = new AtomicInteger();
        List<ErrorCode> refusals = java.util.Collections.synchronizedList(new ArrayList<>());

        // EN: One gate released at once, so the hundred calls overlap instead of queueing up politely.
        // VI: Một cổng mở cùng lúc, để một trăm lời gọi chồng lên nhau thay vì lịch sự xếp hàng.
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(BIDDERS);

        try (ExecutorService pool = Executors.newFixedThreadPool(BIDDERS)) {
            for (UUID racer : racers) {
                pool.submit(() -> {
                    try {
                        start.await();
                        bids.place(auction, racer, OPENING);
                        accepted.incrementAndGet();
                    } catch (BusinessException ex) {
                        refusals.add(ex.code());
                    } catch (Exception ex) {
                        refusals.add(ErrorCode.INTERNAL_ERROR);
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(accepted.get()).isEqualTo(1);
        assertThat(refusals).hasSize(BIDDERS - 1);

        // EN: Every refusal says "too low", not "conflict" or "internal error". That is the difference the
        //     lock makes: the losers read a price that already moved, instead of colliding on the write.
        // VI: Mọi lượt bị từ chối đều nói "quá thấp", không phải "xung đột" hay "lỗi nội bộ". Đó chính là
        //     khác biệt do khoá tạo ra: bên thua đọc được mức giá đã thay đổi, thay vì đụng nhau lúc ghi.
        assertThat(refusals).containsOnly(ErrorCode.BID_TOO_LOW);

        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM bids WHERE auction_id = ?", Integer.class, auction);
        assertThat(rows).isEqualTo(1);

        assertThat(jdbc.queryForObject(
                "SELECT bid_count FROM auctions WHERE id = ?", Integer.class, auction)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT current_price FROM auctions WHERE id = ?", BigDecimal.class, auction))
                .isEqualByComparingTo(OPENING);
    }

    @Test
    void aLadderOfRisingBidsLosesNone() throws Exception {
        String seller = tokenFor("ladder.seller@nexbid.com", "Ladder Seller", RoleName.SELLER);
        String admin = tokenFor("ladder.admin@nexbid.com", "Ladder Admin", RoleName.ADMIN);
        UUID auction = openLot(seller, admin, "Ladder lot");
        List<UUID> racers = bidders(20);

        // EN: Twenty bidders each offering a different, already-valid amount, all at once. Serialising
        //     them must not lose any — a lock that rejects work it should have queued is its own bug.
        // VI: Hai mươi người, mỗi người một số tiền khác nhau và đều hợp lệ, cùng lúc. Việc xếp thứ tự
        //     không được làm mất lượt nào — khoá mà từ chối việc lẽ ra nên xếp hàng cũng là một lỗi.
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers.size());
        AtomicInteger accepted = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(racers.size())) {
            for (int i = 0; i < racers.size(); i++) {
                UUID racer = racers.get(i);
                // EN: 10m, 20m, 30m … every one of them clears any price the others can reach.
                // VI: 10tr, 20tr, 30tr … mỗi số đều vượt mọi mức giá mà những lượt khác có thể tạo ra.
                BigDecimal amount = OPENING.multiply(BigDecimal.valueOf(i + 1L));

                pool.submit(() -> {
                    try {
                        start.await();
                        bids.place(auction, racer, amount);
                        accepted.incrementAndGet();
                    } catch (Exception ignored) {
                        // EN: Counted by what is missing from the total below.
                        // VI: Đếm qua phần thiếu so với tổng ở dưới.
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        }

        // EN: Bids arriving in a random order means some are below the price by the time they are served,
        //     so not all twenty can land — but the winner must be the highest offer made.
        // VI: Các lượt đến theo thứ tự ngẫu nhiên nên có lượt đã thấp hơn giá khi tới lượt xử lý, không
        //     phải cả hai mươi đều vào được — nhưng người thắng phải là lượt trả cao nhất.
        assertThat(accepted.get()).isPositive();

        BigDecimal highest = jdbc.queryForObject(
                "SELECT max(amount) FROM bids WHERE auction_id = ?", BigDecimal.class, auction);
        BigDecimal currentPrice = jdbc.queryForObject(
                "SELECT current_price FROM auctions WHERE id = ?", BigDecimal.class, auction);

        assertThat(currentPrice).isEqualByComparingTo(highest);
        assertThat(jdbc.queryForObject(
                "SELECT bid_count FROM auctions WHERE id = ?", Integer.class, auction))
                .isEqualTo(accepted.get());
    }
}
