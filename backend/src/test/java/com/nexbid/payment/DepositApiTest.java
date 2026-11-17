package com.nexbid.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
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
import com.nexbid.support.Market;
import com.nexbid.support.Market.Account;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.support.VnpayTestSupport;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Deposits on a lot starting at 60 million (the threshold here is 50 million): no bid without one, paying through
 *     VNPay, the IPN being idempotent, the loser owed theirs at the close, the winner after paying, and the admin's
 *     transfer.
 * VI: Tiền cọc trên lô khởi điểm 60 triệu (ngưỡng ở đây là 50 triệu): không có cọc thì không trả giá được, đóng qua VNPay,
 *     IPN idempotent, người thua được trả lại khi đóng lô, người thắng sau khi thanh toán, và lần chuyển khoản của admin.
 */
@SpringBootTest(properties = {"nexbid.scheduler.enabled=false", "nexbid.deposit.threshold=50000000"})
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class DepositApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService users;
    @Autowired
    private AuctionService auctions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private Market market;
    private Account seller;
    private Account admin;

    @BeforeEach
    void setUp() throws Exception {
        market = new Market(mockMvc, users, auctions, jdbc, json);
        seller = market.account("deposit.seller", RoleName.SELLER);
        admin = market.account("deposit.admin", RoleName.ADMIN);
    }

    private ResultActions checkout(Account who, String lot) throws Exception {
        return market.as(who, post("/api/auctions/" + lot + "/deposit/vnpay").contentType(MediaType.APPLICATION_JSON)
                .content("{\"locale\":\"vi\"}"));
    }

    /** EN: Pays the deposit as VNPay's IPN would. / VI: Đóng cọc như IPN của VNPay. */
    private Map<String, String> payDeposit(Account who, String lot) throws Exception {
        String url = VnpayTestSupport.paymentUrlOf(checkout(who, lot).andExpect(status().isOk()));
        Map<String, String> reply = VnpayTestSupport.reply(url, VnpayTestSupport.PAID);
        mockMvc.perform(VnpayTestSupport.withParams(get("/api/payments/vnpay/ipn"), reply))
                .andExpect(jsonPath("$.RspCode").value("00"));
        return reply;
    }

    private String depositStatus(Account who, String lot) {
        return jdbc.queryForObject("""
                SELECT status FROM lot_deposits WHERE user_id = ? AND auction_id = ?::uuid AND status <> 'PENDING'
                ORDER BY created_at DESC LIMIT 1
                """, String.class, who.id(), lot);
    }

    @Test
    void anExpensiveLotNeedsADepositThatComesBack() throws Exception {
        String cheap = market.openLot(seller, admin, "Cheap lot", 10_000_000, null);
        mockMvc.perform(get("/api/auctions/" + cheap + "/deposit")).andExpect(jsonPath("$.data.amount").isEmpty());

        String lot = market.openLot(seller, admin, "Expensive watch", 60_000_000, null);
        mockMvc.perform(get("/api/auctions/" + lot + "/deposit")).andExpect(jsonPath("$.data.amount").value(6000000))
                .andExpect(jsonPath("$.data.held").value(false));

        Account winner = market.account("deposit.winner", RoleName.BUYER);
        Account loser = market.account("deposit.loser", RoleName.BUYER);
        market.bid(winner, lot, 60_000_000).andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("DEPOSIT_REQUIRED"));
        market.as(winner, post("/api/auctions/" + lot + "/auto-bid").contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxAmount\":70000000}")).andExpect(jsonPath("$.code").value("DEPOSIT_REQUIRED"));
        // EN: The deposit must have somewhere to go back to. / VI: Tiền cọc phải có nơi để trả về.
        checkout(winner, lot).andExpect(jsonPath("$.code").value("REFUND_ACCOUNT_REQUIRED"));
        checkout(seller, lot).andExpect(jsonPath("$.code").value("SELLER_CANNOT_BID"));
        market.as(winner, post("/api/auctions/" + cheap + "/deposit/vnpay"))
                .andExpect(jsonPath("$.code").value("DEPOSIT_NOT_NEEDED"));

        market.saveBankAccount(winner);
        market.saveBankAccount(loser);
        Map<String, String> reply = payDeposit(winner, lot);
        // EN: VNPay retrying the IPN changes nothing. / VI: VNPay gửi lại IPN không thay đổi gì.
        mockMvc.perform(VnpayTestSupport.withParams(get("/api/payments/vnpay/ipn"), reply))
                .andExpect(jsonPath("$.RspCode").value("02"));
        mockMvc.perform(VnpayTestSupport.withParams(get("/api/payments/deposits/vnpay/return"), reply))
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.amount").value(6000000));
        checkout(winner, lot).andExpect(jsonPath("$.code").value("DEPOSIT_ALREADY_HELD"));
        payDeposit(loser, lot);

        market.bid(loser, lot, 60_000_000).andExpect(status().isCreated());
        market.bid(winner, lot, 61_000_000).andExpect(status().isCreated());
        market.close(lot);
        assertThat(depositStatus(loser, lot)).isEqualTo("REFUND_DUE");
        assertThat(depositStatus(winner, lot)).isEqualTo("PAID");

        String paymentId = jdbc.queryForObject("SELECT id::text FROM payments WHERE auction_id = ?::uuid", String.class,
                lot);
        VnpayTestSupport.pay(mockMvc, winner.token(), paymentId, "SUCCESS").andExpect(status().isOk());
        assertThat(depositStatus(winner, lot)).isEqualTo("REFUND_DUE");

        String loserDeposit = jdbc.queryForObject(
                "SELECT id::text FROM lot_deposits WHERE user_id = ? AND status = 'REFUND_DUE'", String.class, loser.id());
        market.as(admin, get("/api/admin/deposits")).andExpect(jsonPath("$.data.length()").value(
                org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
        market.as(admin, post("/api/admin/deposits/" + loserDeposit + "/refund").contentType(MediaType.APPLICATION_JSON)
                .content("{\"reference\":\"DEP-" + loserDeposit.substring(0, 8) + "\"}"))
                .andExpect(jsonPath("$.data.status").value("REFUNDED"));
        market.as(loser, get("/api/users/me/deposits")).andExpect(jsonPath("$.data[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.data[0].lotTitle").value("Expensive watch"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'DEPOSIT_REFUNDED' AND entity_id = ?::uuid",
                Integer.class, loserDeposit)).isEqualTo(1);
    }

    @Test
    void aWinnerWhoNeverPaysForfeitsTheDeposit() throws Exception {
        String lot = market.openLot(seller, admin, "Unpaid watch", 80_000_000, null);
        Account winner = market.account("deposit.unpaid", RoleName.BUYER);
        market.saveBankAccount(winner);
        payDeposit(winner, lot);
        market.bid(winner, lot, 80_000_000).andExpect(status().isCreated());
        market.close(lot);
        jdbc.update("UPDATE payments SET expired_at = now() - interval '1 minute' WHERE auction_id = ?::uuid", lot);
        paymentsExpire();
        assertThat(depositStatus(winner, lot)).isEqualTo("FORFEITED");
    }

    @Autowired
    private PaymentService payments;

    private void paymentsExpire() {
        payments.expireOverdue(java.time.Instant.now(), 100);
    }
}
