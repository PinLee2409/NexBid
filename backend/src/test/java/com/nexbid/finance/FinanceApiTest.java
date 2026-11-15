package com.nexbid.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.auction.AuctionService;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.support.VnpayTestSupport;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: A sale paid through VNPay shows up in the day's reconciliation and the charges CSV, and once completed in revenue.
 * VI: Một giao dịch trả qua VNPay hiện trong đối soát của ngày và file CSV các lần thu, và khi hoàn tất thì vào doanh thu.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class FinanceApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private AuctionService auctions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Insight User","email":"%s","password":"supersecret"}
                """.formatted(email)));
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"supersecret"}
                """.formatted(email))).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    /** EN: An approved, open lot in the given category. / VI: Một lô đã duyệt, đang mở, trong danh mục cho trước. */
    private String openLot(String seller, String admin, String name, String categoryId) throws Exception {
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","description":"A long description of the item.",
                         "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                        """.formatted(name, categoryId))).andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();
        String auctionBody = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"productId":"%s","startingPrice":1000000,"minimumIncrement":100000,
                         "startTime":"%s","endTime":"%s"}
                        """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)),
                        Instant.now().plus(Duration.ofHours(4))))).andReturn().getResponse().getContentAsString();
        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auctionId);
        return auctionId;
    }

    /** EN: A category of the test's own. / VI: Một danh mục riêng của test. */
    private String category(String admin, String name) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    @Test
    void aPaidSaleIsReconciledExportedAndCountedAsRevenue() throws Exception {
        String seller = tokenFor("fin.seller@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("fin.admin@nexbid.com", RoleName.ADMIN);
        String buyer = tokenFor("fin.buyer@nexbid.com", RoleName.BUYER);
        String lot = openLot(seller, admin, "Ledger lot, \\\"special\\\"", category(admin, "Finance clocks"));
        mockMvc.perform(post("/api/auctions/" + lot + "/bids").header("Authorization", "Bearer " + buyer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000000}")).andExpect(status().isCreated());
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", lot);
        auctions.endDueAuctions(Instant.now(), 200);
        String paymentId = jdbc.queryForObject("SELECT id::text FROM payments WHERE auction_id = ?::uuid", String.class,
                lot);
        VnpayTestSupport.pay(mockMvc, buyer, paymentId, "SUCCESS").andExpect(status().isOk());

        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toString();
        mockMvc.perform(get("/api/admin/finance/reconciliation").param("from", today).param("to", today)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days.length()").value(1))
                .andExpect(jsonPath("$.data.total.unmatched").value(0))
                .andExpect(jsonPath("$.data.total.applied").value(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1050000.0)));
        mockMvc.perform(get("/api/admin/finance/reconciliation").param("from", today).param("to", "2020-01-01")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String csv = new String(mockMvc.perform(get("/api/admin/finance/charges.csv").param("from", today)
                        .param("to", today).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("nexbid-vnpay-")))
                .andReturn().getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).startsWith(
                "﻿time_vn,txn_ref,vnpay_transaction_no,bank,amount,outcome,payment_id,lot,buyer\r\n");
        assertThat(csv).contains(",1050000.00,APPLIED," + paymentId
                + ",\"Ledger lot, \"\"special\"\"\",fin.buyer@nexbid.com");
        mockMvc.perform(get("/api/admin/finance/charges.csv").param("from", today).param("to", today)
                .header("Authorization", "Bearer " + seller)).andExpect(status().isForbidden());

        String orderId = jdbc.queryForObject("SELECT id::text FROM orders WHERE payment_id = ?::uuid", String.class,
                paymentId);
        mockMvc.perform(post("/api/seller/orders/" + orderId + "/ship").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/users/me/orders/" + orderId + "/received").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/finance/revenue").param("months", "2").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.months.length()").value(2))
                .andExpect(jsonPath("$.data.categories[?(@.name == 'Finance clocks')].fees",
                        org.hamcrest.Matchers.contains(50000.0)))
                .andExpect(jsonPath("$.data.sellers[?(@.name == 'Insight User')]").isNotEmpty());
        String revenue = mockMvc.perform(get("/api/admin/finance/revenue.csv").header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(revenue).contains("month,sales,gross,platform_fees,shipping_to_sellers");
    }

    @Test
    void spreadsheetCellsCannotRunFormulas() {
        assertThat(FinanceController.cell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(FinanceController.cell("-1500")).isEqualTo("-1500");
        assertThat(FinanceController.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(FinanceController.cell(null)).isEmpty();
    }
}
