package com.nexbid.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexbid.auction.AuctionService;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The winner's payment (guide §32, spec §17): opened at the close, paid only by the winner, retried
 *     after a failure, and expired — with the lot released — once the window runs out.
 * VI: Khoản thanh toán của người thắng (guide §32, spec §17): mở lúc đóng phiên, chỉ người thắng được trả,
 *     thử lại được sau khi hỏng, và hết hạn — kèm việc trả lô — khi quá thời gian cho phép.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class PaymentApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionService auctions;

    @Autowired
    private PaymentService payments;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private ObjectMapper objectMapper;

    /** EN: A lot that has been won, and the ids a test needs about it. / VI: Một lô đã có người thắng, kèm các id mà test cần. */
    private record Won(String auctionId, String productId, String paymentId, String winner, String seller) {
    }

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

    private UUID idOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("data").get("id").asString());
    }

    /** EN: Approved and open. Returns (auctionId, productId). / VI: Đã duyệt và đang mở. Trả về (auctionId, productId). */
    private String[] openLot(String seller, String admin, String name) throws Exception {
        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
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

        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' "
                + "WHERE id = ?::uuid", auctionId);

        return new String[] { auctionId, productId };
    }

    private void close(String auctionId) {
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", auctionId);
        auctions.endDueAuctions(Instant.now(), 200);
    }

    /** EN: A lot the winner has just won at 10.5m. / VI: Một lô người thắng vừa thắng ở 10,5 triệu. */
    private Won wonLot(String tag) throws Exception {
        String seller = tokenFor("pay.seller." + tag + "@nexbid.com", "Seller " + tag, RoleName.SELLER);
        String admin = tokenFor("pay.admin." + tag + "@nexbid.com", "Admin " + tag, RoleName.ADMIN);
        String rival = tokenFor("pay.rival." + tag + "@nexbid.com", "Rival " + tag, RoleName.BUYER);
        String winner = tokenFor("pay.winner." + tag + "@nexbid.com", "Winner " + tag, RoleName.BUYER);
        String[] lot = openLot(seller, admin, "Lot " + tag);

        bid(rival, lot[0], "10000000");
        bid(winner, lot[0], "10500000");
        close(lot[0]);

        String paymentId = jdbc.queryForObject(
                "SELECT id::text FROM payments WHERE auction_id = ?::uuid", String.class, lot[0]);
        return new Won(lot[0], lot[1], paymentId, winner, seller);
    }

    private void bid(String token, String auctionId, String amount) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    private ResultActions pay(String token, String paymentId, String outcome) throws Exception {
        return mockMvc.perform(post("/api/payments/" + paymentId + "/pay")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"" + outcome + "\"}"));
    }

    private String stored(String paymentId) {
        return jdbc.queryForObject("SELECT status FROM payments WHERE id = ?::uuid", String.class, paymentId);
    }

    private void makeOverdue(String paymentId) {
        jdbc.update("UPDATE payments SET expired_at = now() - interval '1 second' WHERE id = ?::uuid", paymentId);
    }

    /**
     * EN: The expiry job scans every payment in the shared test database, so each test that counts what it
     *     expired first clears what earlier tests left overdue.
     * VI: Job hết hạn quét mọi khoản trong database test dùng chung, nên test nào đếm số khoản bị hết hạn thì
     *     phải dọn trước những gì test khác để lại đã quá hạn.
     */
    private void expireLeftovers() {
        while (payments.expireOverdue(Instant.now(), 500) > 0) {
            // EN: Until nothing is overdue. / VI: Tới khi không còn gì quá hạn.
        }
    }

    private int notices(UUID user, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?",
                Integer.class, user, type);
    }

    @Test
    void closingAWonLotOpensAPaymentForTheWinnerAtTheFinalPrice() throws Exception {
        Won won = wonLot("open");

        var row = jdbc.queryForMap("""
                SELECT p.user_id, p.amount, p.status, p.expired_at, a.end_time
                FROM payments p JOIN auctions a ON a.id = p.auction_id WHERE p.id = ?::uuid
                """, won.paymentId());

        assertThat(row.get("user_id")).isEqualTo(idOf(won.winner()));
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat((java.math.BigDecimal) row.get("amount")).isEqualByComparingTo("10500000");
        // EN: 48 hours from the close itself, not from whenever the scheduler happened to notice.
        // VI: 48 giờ tính từ chính giờ đóng, không từ lúc scheduler tình cờ nhận ra.
        assertThat(((java.sql.Timestamp) row.get("expired_at")).toInstant())
                .isEqualTo(((java.sql.Timestamp) row.get("end_time")).toInstant().plus(Duration.ofHours(48)));

        mockMvc.perform(get("/api/users/me/payments").header("Authorization", "Bearer " + won.winner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].payment.id").value(won.paymentId()))
                .andExpect(jsonPath("$.data[0].payment.status").value("PENDING"))
                .andExpect(jsonPath("$.data[0].auction.product.name").value("Lot open"));
    }

    @Test
    void aLotThatSoldNothingBillsNobody() throws Exception {
        String seller = tokenFor("pay.seller.none@nexbid.com", "Seller None", RoleName.SELLER);
        String admin = tokenFor("pay.admin.none@nexbid.com", "Admin None", RoleName.ADMIN);
        String[] lot = openLot(seller, admin, "Unsold");

        close(lot[0]);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE auction_id = ?::uuid",
                Integer.class, lot[0])).isZero();
    }

    @Test
    void onlyTheWinnerCanSeeOrPay() throws Exception {
        Won won = wonLot("owner");
        String stranger = tokenFor("pay.stranger@nexbid.com", "Stranger", RoleName.BUYER);

        // EN: Someone else's payment answers exactly like one that does not exist.
        // VI: Khoản thanh toán của người khác trả lời y như khoản không tồn tại.
        for (String token : List.of(stranger, won.seller())) {
            mockMvc.perform(get("/api/payments/" + won.paymentId()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
            pay(token, won.paymentId(), "SUCCESS")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        }
        pay(stranger, UUID.randomUUID().toString(), "SUCCESS").andExpect(status().isNotFound());

        assertThat(stored(won.paymentId())).isEqualTo("PENDING");
    }

    @Test
    void payingSucceedsOnceAndOnlyOnce() throws Exception {
        Won won = wonLot("success");

        pay(won.winner(), won.paymentId(), "SUCCESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.status").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Payment successful"));

        pay(won.winner(), won.paymentId(), "SUCCESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_PAID"));
        pay(won.winner(), won.paymentId(), "FAILED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_PAID"));

        UUID winnerId = idOf(won.winner());
        await().atMost(Duration.ofSeconds(10)).until(() -> notices(winnerId, "PAYMENT_SUCCESS") == 1);
    }

    @Test
    void aFailedAttemptCanBeRetried() throws Exception {
        Won won = wonLot("retry");

        pay(won.winner(), won.paymentId(), "FAILED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Payment failed"));

        // EN: A declined card is not the end of the sale; the window is still open.
        // VI: Thẻ bị từ chối không phải là hết giao dịch; thời hạn vẫn còn mở.
        pay(won.winner(), won.paymentId(), "SUCCESS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payment.status").value("SUCCESS"));
    }

    @Test
    void theDeadlineIsJudgedByTheClockEvenBeforeTheExpiryJobRuns() throws Exception {
        Won won = wonLot("late");
        makeOverdue(won.paymentId());

        // EN: Still PENDING in the table, but it already reads EXPIRED and cannot be paid.
        // VI: Trong bảng vẫn là PENDING, nhưng đọc ra đã là EXPIRED và không trả được nữa.
        mockMvc.perform(get("/api/payments/" + won.paymentId()).header("Authorization", "Bearer " + won.winner()))
                .andExpect(jsonPath("$.data.payment.status").value("EXPIRED"));
        pay(won.winner(), won.paymentId(), "SUCCESS")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_EXPIRED"));

        assertThat(stored(won.paymentId())).isEqualTo("PENDING");
    }

    @Test
    void anExpiredPaymentCancelsTheSaleAndGivesTheProductBack() throws Exception {
        Won won = wonLot("expire");
        expireLeftovers();
        makeOverdue(won.paymentId());

        assertThat(payments.expireOverdue(Instant.now(), 200)).isEqualTo(1);

        assertThat(stored(won.paymentId())).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, won.auctionId()))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?::uuid", String.class, won.productId()))
                .isEqualTo("AVAILABLE");

        // EN: The seller can put it up again. / VI: Người bán đăng lại được.
        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + won.seller())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":9000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(won.productId(),
                                Instant.now().plus(Duration.ofMinutes(30)), Instant.now().plus(Duration.ofHours(4)))))
                .andExpect(status().isCreated());

        // EN: The winner still sees what happened to their payment, and the lot it was for.
        // VI: Người thắng vẫn thấy chuyện gì đã xảy ra với khoản thanh toán của mình, và lô tương ứng.
        mockMvc.perform(get("/api/users/me/payments").header("Authorization", "Bearer " + won.winner()))
                .andExpect(jsonPath("$.data[0].payment.status").value("EXPIRED"))
                .andExpect(jsonPath("$.data[0].auction.product.name").value("Lot expire"));

        UUID winnerId = idOf(won.winner());
        await().atMost(Duration.ofSeconds(10)).until(() -> notices(winnerId, "PAYMENT_EXPIRED") == 1);
    }

    @Test
    void aFailedPaymentExpiresTooButAPaidOneNeverDoes() throws Exception {
        Won failed = wonLot("failed-expire");
        Won paid = wonLot("paid-never");

        pay(failed.winner(), failed.paymentId(), "FAILED");
        pay(paid.winner(), paid.paymentId(), "SUCCESS");
        makeOverdue(failed.paymentId());
        makeOverdue(paid.paymentId());

        payments.expireOverdue(Instant.now(), 200);

        assertThat(stored(failed.paymentId())).isEqualTo("EXPIRED");
        assertThat(stored(paid.paymentId())).isEqualTo("SUCCESS");
        // EN: Completed by the payment (function 31), and the expiry job did not undo it.
        // VI: Được hoàn tất nhờ thanh toán (chức năng 31), và job hết hạn không đảo ngược nó.
        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, paid.auctionId()))
                .isEqualTo("COMPLETED");
    }

    @Test
    void tenSimultaneousPaymentsChargeOnce() throws Exception {
        Won won = wonLot("double");
        UUID winnerId = idOf(won.winner());
        UUID paymentId = UUID.fromString(won.paymentId());

        // EN: A double-click, a retrying client, two tabs: the lock lets exactly one through.
        // VI: Bấm đúp, client tự thử lại, hai tab: khoá chỉ cho đúng một lần đi qua.
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(10);

        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 10; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        payments.pay(winnerId, paymentId, PaymentOutcome.SUCCESS);
                        results.add("PAID");
                    } catch (com.nexbid.common.exception.BusinessException ex) {
                        results.add(ex.code().name());
                    } catch (Exception ex) {
                        results.add("UNEXPECTED " + ex);
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(results).filteredOn("PAID"::equals).hasSize(1);
        assertThat(results).filteredOn("PAYMENT_ALREADY_PAID"::equals).hasSize(9);

        await().during(Duration.ofMillis(800)).atMost(Duration.ofSeconds(5))
                .until(() -> notices(winnerId, "PAYMENT_SUCCESS") == 1);
    }

    @Test
    void anExpiryThatWasAlreadyWaitingLetsAPaymentThatGotInFirstStand() throws Exception {
        Won won = wonLot("photo");
        expireLeftovers();
        jdbc.update("UPDATE payments SET expired_at = now() + interval '400 milliseconds' WHERE id = ?::uuid",
                won.paymentId());

        // EN: Plays a payment committing just before the deadline: it holds the row and marks it paid.
        // VI: Đóng vai một lần thanh toán đang commit ngay trước hạn chót: nó giữ dòng và đánh dấu đã trả.
        CountDownLatch holding = new CountDownLatch(1);

        try (ExecutorService inFlight = Executors.newSingleThreadExecutor()) {
            var committed = inFlight.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM payments WHERE id = ?::uuid FOR UPDATE", UUID.class, won.paymentId());
                jdbc.update("UPDATE payments SET status = 'SUCCESS' WHERE id = ?::uuid", won.paymentId());
                holding.countDown();
                try {
                    Thread.sleep(1200);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }));

            assertThat(holding.await(10, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(600);

            // EN: The deadline has passed and the job waits on the lock; once it frees, the row is PAID and
            //     the job leaves it alone instead of cancelling a sale that was paid for.
            // VI: Hạn chót đã qua và job chờ ở khoá; khi khoá mở, dòng đã là ĐÃ TRẢ và job để yên nó thay vì
            //     huỷ một giao dịch đã được trả tiền.
            assertThat(payments.expireOverdue(Instant.now(), 200)).isZero();
            committed.get(10, TimeUnit.SECONDS);
        }

        assertThat(stored(won.paymentId())).isEqualTo("SUCCESS");
        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, won.auctionId()))
                .isEqualTo("ENDED");
    }

    @Test
    void theOutcomeMustBeOneOfTheTwoDemoChoices() throws Exception {
        Won won = wonLot("bad-body");

        pay(won.winner(), won.paymentId(), "MAYBE").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/payments/" + won.paymentId() + "/pay")
                        .header("Authorization", "Bearer " + won.winner())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void theEndpointsNeedASignedInCaller() throws Exception {
        mockMvc.perform(get("/api/users/me/payments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/payments/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/payments/" + UUID.randomUUID() + "/pay")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isUnauthorized());
    }
}
