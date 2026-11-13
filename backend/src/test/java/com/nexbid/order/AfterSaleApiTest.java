package com.nexbid.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nexbid.auction.AuctionService;
import com.nexbid.notification.CompletionReminders;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.support.VnpayTestSupport;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: What happens after the money: the buyer may dispute, an admin decides, a silent buyer's order completes on its
 *     own, and each completed sale becomes money owed to the seller until an admin records the bank transfer.
 * VI: Chuyện xảy ra sau khi trả tiền: người mua có thể khiếu nại, admin quyết định, đơn của người mua im lặng tự hoàn
 *     tất, và mỗi giao dịch hoàn tất trở thành khoản nợ người bán cho tới khi admin ghi nhận lần chuyển khoản.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import({TestInfrastructure.class, CompletionReminders.class})
class AfterSaleApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionService auctions;

    @Autowired
    private OrderService orders;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private record Sale(String auctionId, String orderId, String paymentId, String buyer, String seller, String admin,
            UUID sellerId, UUID buyerId) {
    }

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

    private UUID idOf(String email) {
        return users.findByEmail(email).orElseThrow().id();
    }

    /** EN: A lot won for 12,000,000 and paid. / VI: Một lô thắng với giá 12.000.000 và đã trả tiền. */
    private Sale paidSale(String tag) throws Exception {
        String sellerEmail = "as.seller." + tag + "@nexbid.com";
        String buyerEmail = "as.buyer." + tag + "@nexbid.com";
        String seller = tokenFor(sellerEmail, "Seller " + tag, RoleName.SELLER);
        String admin = tokenFor("as.admin." + tag + "@nexbid.com", "Admin " + tag, RoleName.ADMIN);
        String buyer = tokenFor(buyerEmail, "Buyer " + tag, RoleName.BUYER);

        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"After sale %s","description":"A long description of the item.",
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
        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auctionId);
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids").header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":12000000}"))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", auctionId);
        auctions.endDueAuctions(Instant.now(), 200);

        String paymentId = jdbc.queryForObject("SELECT id::text FROM payments WHERE auction_id = ?::uuid",
                String.class, auctionId);
        VnpayTestSupport.pay(mockMvc, buyer, paymentId, "SUCCESS").andExpect(status().isOk());
        String orderId = jdbc.queryForObject("SELECT id::text FROM orders WHERE auction_id = ?::uuid",
                String.class, auctionId);
        return new Sale(auctionId, orderId, paymentId, buyer, seller, admin, idOf(sellerEmail), idOf(buyerEmail));
    }

    private ResultActions ship(Sale sale) throws Exception {
        return mockMvc.perform(post("/api/seller/orders/" + sale.orderId() + "/ship")
                .header("Authorization", "Bearer " + sale.seller()));
    }

    private ResultActions dispute(String token, String orderId, String reason, String details) throws Exception {
        return mockMvc.perform(post("/api/users/me/orders/" + orderId + "/dispute")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"%s\",\"details\":\"%s\"}".formatted(reason, details)));
    }

    private String disputeIdOf(Sale sale) {
        return jdbc.queryForObject("SELECT id::text FROM order_disputes WHERE order_id = ?::uuid ORDER BY created_at DESC LIMIT 1",
                String.class, sale.orderId());
    }

    /** EN: A refund carries the bank reference of the transfer. / VI: Hoàn tiền kèm mã giao dịch của lần chuyển khoản. */
    private ResultActions resolve(Sale sale, String disputeId, boolean refund, String note) throws Exception {
        return resolve(sale, disputeId, refund, note, refund ? "FT2611140099" : null);
    }

    private ResultActions resolve(Sale sale, String disputeId, boolean refund, String note, String reference)
            throws Exception {
        return mockMvc.perform(post("/api/admin/disputes/" + disputeId + "/resolve")
                .header("Authorization", "Bearer " + sale.admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"refund\":%s,\"note\":\"%s\"%s}".formatted(refund, note,
                        reference == null ? "" : ",\"reference\":\"" + reference + "\"")));
    }

    private void saveBankAccount(String token) throws Exception {
        mockMvc.perform(put("/api/users/me/bank-account").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"Vietcombank\",\"accountNumber\":\"0123 4567 89\",\"accountHolder\":\"Nguyen Van A\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNumber").value("0123456789"))
                .andExpect(jsonPath("$.data.accountHolder").value("NGUYEN VAN A"));
    }

    private List<String> mailsTo(UUID userId) {
        return jdbc.queryForList("SELECT kind FROM email_outbox WHERE user_id = ? ORDER BY created_at", String.class,
                userId);
    }

    private List<String> noticesTo(UUID userId) {
        return jdbc.queryForList("SELECT type FROM notifications WHERE user_id = ? ORDER BY created_at", String.class,
                userId);
    }

    /** EN: "action old → new" for one entity. / VI: "hành động cũ → mới" của một đối tượng. */
    private List<String> auditOf(String entityId) {
        return jdbc.queryForList("""
                SELECT action || ' ' || COALESCE(old_value, '-') || ' -> ' || COALESCE(new_value, '-')
                FROM audit_logs WHERE entity_id = ?::uuid ORDER BY position
                """, String.class, entityId);
    }

    private String statusOf(String table, String id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?::uuid", String.class, id);
    }

    @Test
    void aDisputedShipmentWaitsForTheAdminWhoMayLetTheSaleStand() throws Exception {
        Sale sale = paidSale("reject");
        ship(sale).andExpect(status().isOk());

        dispute(sale.buyer(), sale.orderId(), "NOT_RECEIVED", "Tracking has not moved for a week.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.order.status").value("DISPUTED"))
                .andExpect(jsonPath("$.data.order.dispute.reason").value("NOT_RECEIVED"))
                .andExpect(jsonPath("$.data.order.dispute.status").value("OPEN"));
        assertThat(mailsTo(sale.sellerId())).contains("DISPUTE_OPENED");

        // EN: A second complaint, receipt, and the clock all wait for the decision.
        // VI: Khiếu nại thứ hai, xác nhận đã nhận, và đồng hồ tự hoàn tất đều phải chờ quyết định.
        dispute(sale.buyer(), sale.orderId(), "OTHER", "Another complaint about it.").andExpect(status().isConflict());
        mockMvc.perform(post("/api/users/me/orders/" + sale.orderId() + "/received")
                .header("Authorization", "Bearer " + sale.buyer())).andExpect(status().isConflict());
        jdbc.update("UPDATE orders SET shipped_at = now() - interval '30 days' WHERE id = ?::uuid", sale.orderId());
        orders.completeOverdue(Instant.now().minus(Duration.ofDays(7)), 200);
        assertThat(statusOf("orders", sale.orderId())).isEqualTo("DISPUTED");

        mockMvc.perform(get("/api/admin/disputes").header("Authorization", "Bearer " + sale.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.entry.order.id == '" + sale.orderId() + "')].dispute.details")
                        .value("Tracking has not moved for a week."));
        mockMvc.perform(get("/api/admin/disputes").header("Authorization", "Bearer " + sale.seller()))
                .andExpect(status().isForbidden());

        String disputeId = disputeIdOf(sale);
        resolve(sale, disputeId, false, "Carrier confirms delivery on the 12th.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.order.dispute.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.order.dispute.resolutionNote").value("Carrier confirms delivery on the 12th."));
        resolve(sale, disputeId, true, "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPUTE_CLOSED"));

        assertThat(mailsTo(sale.buyerId())).contains("DISPUTE_REJECTED");
        assertThat(mailsTo(sale.sellerId())).contains("DISPUTE_SELLER_REJECTED");
        assertThat(noticesTo(sale.buyerId())).contains("DISPUTE_RESOLVED");
        assertThat(noticesTo(sale.sellerId())).contains("DISPUTE_OPENED", "DISPUTE_SELLER_RESOLVED");
        assertThat(auditOf(disputeId)).containsExactly(
                "DISPUTE_OPENED - -> NOT_RECEIVED",
                "DISPUTE_REJECTED OPEN -> REJECTED: Carrier confirms delivery on the 12th.");
        // EN: Completed from DISPUTED, not from PROCESSING. / VI: Hoàn tất từ DISPUTED, không phải từ PROCESSING.
        assertThat(auditOf(sale.orderId())).contains("ORDER_COMPLETED DISPUTED -> COMPLETED");
        // EN: Completed, so the seller is owed the price less 5%. / VI: Đã hoàn tất, nên sàn nợ người bán giá bán trừ 5%.
        assertThat(jdbc.queryForObject("SELECT net FROM seller_earnings WHERE order_id = ?::uuid", BigDecimal.class,
                sale.orderId())).isEqualByComparingTo("11400000");
    }

    @Test
    void anUpheldDisputeRefundsTheBuyerAndOwesTheSellerNothing() throws Exception {
        Sale sale = paidSale("upheld");

        dispute(sale.buyer(), sale.orderId(), "OTHER", "The seller stopped answering me.")
                .andExpect(status().isCreated());
        // EN: A refund needs the transfer's reference, and an account to have sent it to.
        // VI: Hoàn tiền cần mã giao dịch, và một tài khoản đã nhận số tiền đó.
        resolve(sale, disputeIdOf(sale), true, "Seller unreachable.", " ").andExpect(status().isBadRequest());
        resolve(sale, disputeIdOf(sale), true, "Seller unreachable.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_ACCOUNT_REQUIRED"));
        assertThat(statusOf("orders", sale.orderId())).isEqualTo("DISPUTED");

        saveBankAccount(sale.buyer());
        resolve(sale, disputeIdOf(sale), true, "Seller unreachable.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.payment.status").value("REFUNDED"))
                .andExpect(jsonPath("$.data.order.dispute.status").value("REFUNDED"));

        assertThat(statusOf("payments", sale.paymentId())).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT reference || ' ' || account_holder FROM payment_refunds WHERE payment_id = ?::uuid",
                String.class, sale.paymentId())).isEqualTo("FT2611140099 NGUYEN VAN A");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seller_earnings WHERE order_id = ?::uuid", Integer.class,
                sale.orderId())).isZero();
        assertThat(mailsTo(sale.buyerId())).contains("PAYMENT_REFUNDED");
        assertThat(mailsTo(sale.sellerId())).contains("DISPUTE_SELLER_REFUNDED");
        assertThat(noticesTo(sale.buyerId())).contains("ORDER_REFUNDED").doesNotContain("DISPUTE_RESOLVED");
        assertThat(noticesTo(sale.sellerId())).contains("DISPUTE_OPENED", "DISPUTE_SELLER_RESOLVED");
        assertThat(auditOf(sale.buyerId().toString())).contains("BANK_ACCOUNT_CHANGED - -> Vietcombank ••••6789");
        assertThat(auditOf(disputeIdOf(sale))).contains("DISPUTE_REFUNDED OPEN -> REFUNDED: Seller unreachable.");
    }

    @Test
    void theBuyerIsRemindedOnceBeforeAShipmentCompletesOnItsOwn() throws Exception {
        Sale sale = paidSale("remind");
        ship(sale).andExpect(status().isOk());

        // EN: Shipped just now: completes in 7 days, outside the 2-day reminder. / VI: Vừa gửi: 7 ngày nữa mới hoàn tất, ngoài khoảng nhắc 2 ngày.
        Instant now = Instant.now();
        assertThat(orders.completingBetween(now, now.plus(Duration.ofDays(2))))
                .noneMatch(order -> order.orderId().toString().equals(sale.orderId()));

        jdbc.update("UPDATE orders SET shipped_at = now() - interval '6 days' WHERE id = ?::uuid", sale.orderId());
        assertThat(orders.completingBetween(now, now.plus(Duration.ofDays(2))))
                .anyMatch(order -> order.orderId().toString().equals(sale.orderId())
                        && order.completesAt().isAfter(now) && order.completesAt().isBefore(now.plus(Duration.ofDays(2))));

        reminders.send(now);
        reminders.send(now.plusSeconds(60));
        assertThat(noticesTo(sale.buyerId()).stream().filter("ORDER_COMPLETING"::equals)).hasSize(1);
        assertThat(mailsTo(sale.buyerId()).stream().filter("ORDER_COMPLETING"::equals)).hasSize(1);
    }

    @Autowired
    private CompletionReminders reminders;

    @Test
    void onlyTheBuyerMayDisputeAndOnlyWithAReason() throws Exception {
        Sale sale = paidSale("rules");
        String stranger = tokenFor("as.stranger@nexbid.com", "Stranger", RoleName.BUYER);

        dispute(stranger, sale.orderId(), "OTHER", "This is not my order at all.").andExpect(status().isNotFound());
        dispute(sale.buyer(), sale.orderId(), "OTHER", "short").andExpect(status().isBadRequest());
        dispute(sale.buyer(), sale.orderId(), "BORED", "I simply changed my mind.").andExpect(status().isBadRequest());
        assertThat(statusOf("orders", sale.orderId())).isEqualTo("PAID");
    }

    @Test
    void aShipmentTheBuyerNeverConfirmsCompletesOnItsOwn() throws Exception {
        Sale sale = paidSale("silent");
        ship(sale).andExpect(status().isOk());

        orders.completeOverdue(Instant.now().minus(Duration.ofDays(7)), 200);
        assertThat(statusOf("orders", sale.orderId())).isEqualTo("PROCESSING");

        jdbc.update("UPDATE orders SET shipped_at = now() - interval '8 days' WHERE id = ?::uuid", sale.orderId());
        assertThat(orders.completeOverdue(Instant.now().minus(Duration.ofDays(7)), 200)).isPositive();

        assertThat(statusOf("orders", sale.orderId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT auto_completed FROM orders WHERE id = ?::uuid", Boolean.class,
                sale.orderId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM seller_earnings WHERE order_id = ?::uuid", Integer.class,
                sale.orderId())).isOne();
        mockMvc.perform(get("/api/users/me/orders/" + sale.orderId()).header("Authorization", "Bearer " + sale.buyer()))
                .andExpect(jsonPath("$.data.order.autoCompleted").value(true));
    }

    @Test
    void anAdminPaysTheSellersWholeBalanceToTheirBankAccountOnce() throws Exception {
        Sale sale = paidSale("payout");
        ship(sale).andExpect(status().isOk());
        mockMvc.perform(post("/api/users/me/orders/" + sale.orderId() + "/received")
                .header("Authorization", "Bearer " + sale.buyer())).andExpect(status().isOk());

        mockMvc.perform(get("/api/seller/earnings").header("Authorization", "Bearer " + sale.seller()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(11400000))
                .andExpect(jsonPath("$.data.feePercent").value(5))
                .andExpect(jsonPath("$.data.earnings[0].fee").value(600000))
                .andExpect(jsonPath("$.data.earnings[0].auction.product.name").value("After sale payout"))
                .andExpect(jsonPath("$.data.account").doesNotExist());
        mockMvc.perform(get("/api/seller/earnings").header("Authorization", "Bearer " + sale.buyer()))
                .andExpect(status().isForbidden());

        String pay = "{\"sellerId\":\"%s\",\"reference\":\"FT2611130001\"}".formatted(sale.sellerId());
        mockMvc.perform(post("/api/admin/payouts").header("Authorization", "Bearer " + sale.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(pay))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BANK_ACCOUNT_REQUIRED"));

        saveBankAccount(sale.seller());
        mockMvc.perform(get("/api/users/me/bank-account").header("Authorization", "Bearer " + sale.seller()))
                .andExpect(jsonPath("$.data.bankName").value("Vietcombank"));
        mockMvc.perform(put("/api/users/me/bank-account").header("Authorization", "Bearer " + sale.seller())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankName\":\"Vietcombank\",\"accountNumber\":\"12ab\",\"accountHolder\":\"X\"}"))
                .andExpect(status().isBadRequest());
        assertThat(auditOf(sale.sellerId().toString())).contains("BANK_ACCOUNT_CHANGED - -> Vietcombank ••••6789");
        mockMvc.perform(get("/api/admin/payouts/due").header("Authorization", "Bearer " + sale.admin()))
                .andExpect(jsonPath("$.data[?(@.sellerId == '" + sale.sellerId() + "')].balance", hasItem(11400000.0)))
                .andExpect(jsonPath("$.data[?(@.sellerId == '" + sale.sellerId() + "')].account.bankName").value("Vietcombank"));

        mockMvc.perform(post("/api/admin/payouts").header("Authorization", "Bearer " + sale.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(pay))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.amount").value(11400000));
        mockMvc.perform(post("/api/admin/payouts").header("Authorization", "Bearer " + sale.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(pay))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NOTHING_TO_PAY_OUT"));

        mockMvc.perform(get("/api/seller/earnings").header("Authorization", "Bearer " + sale.seller()))
                .andExpect(jsonPath("$.data.balance").value(0))
                .andExpect(jsonPath("$.data.paidOut").value(11400000))
                .andExpect(jsonPath("$.data.payouts[0].reference").value("FT2611130001"));
        assertThat(mailsTo(sale.sellerId())).contains("SELLER_PAYOUT");
        assertThat(noticesTo(sale.sellerId())).contains("PAYOUT_SENT");
        String payoutId = jdbc.queryForObject("SELECT id::text FROM seller_payouts WHERE seller_id = ?",
                String.class, sale.sellerId());
        assertThat(auditOf(payoutId)).containsExactly("PAYOUT_RECORDED - -> 11400000.00 ref FT2611130001");
        mockMvc.perform(post("/api/admin/payouts").header("Authorization", "Bearer " + sale.seller())
                        .contentType(MediaType.APPLICATION_JSON).content(pay))
                .andExpect(status().isForbidden());
    }
}
