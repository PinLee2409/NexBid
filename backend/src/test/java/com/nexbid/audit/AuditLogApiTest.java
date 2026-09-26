package com.nexbid.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexbid.auction.AuctionAuditEvent;
import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionStatus;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** EN: The nine required actions are captured from real flows, including the scheduler and a rejected
 *     transaction. Only admins can read the ledger.
 * VI: Chín hành động cần có được ghi từ luồng thật, kể cả scheduler và transaction rollback.
 *     Chỉ admin được xem nhật ký. */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class AuditLogApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired AuctionService auctions;
    @Autowired TransactionTemplate tx;
    @Autowired ApplicationEventPublisher events;

    private record Account(UUID id, String token) {
    }

    private Account account(String tag, RoleName role) throws Exception {
        String email = "audit." + tag + "." + UUID.randomUUID() + "@nexbid.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Audit %s","email":"%s","password":"supersecret"}
                """.formatted(tag, email))).andExpect(status().isCreated());
        users.grantRole(email, role);
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .with(request -> { request.setRemoteAddr("203.0.113.7"); return request; })
                        .content("""
                                {"email":"%s","password":"supersecret"}
                                """.formatted(email)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).get("data").get("accessToken").asString();
        String me = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(UUID.fromString(json.readTree(me).get("data").get("id").asString()), token);
    }

    private UUID auction(Account seller, String tag) throws Exception {
        String categories = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        String category = json.readTree(categories).get("data").get(0).get("id").asString();
        String productBody = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + seller.token())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Audit %s","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(tag, category)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String product = json.readTree(productBody).get("data").get("id").asString();
        String auctionBody = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller.token())
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s",
                                 "antiSnipingEnabled":true,"antiSnipingWindowSeconds":30,"extensionSeconds":120}
                                """.formatted(product, Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(auctionBody).get("data").get("id").asString());
        mockMvc.perform(post("/api/seller/auctions/" + id + "/submit")
                        .header("Authorization", "Bearer " + seller.token()))
                .andExpect(status().isOk());
        return id;
    }

    private Set<String> actions() {
        return jdbc.queryForList("SELECT DISTINCT action FROM audit_logs", String.class)
                .stream().collect(Collectors.toSet());
    }

    @Test
    void capturesImportantActionsAndOnlyAdminsCanReadThem() throws Exception {
        Account seller = account("seller", RoleName.SELLER);
        Account admin = account("admin", RoleName.ADMIN);
        Account buyer = account("buyer", RoleName.BUYER);
        Account target = account("target", RoleName.BUYER);

        UUID live = auction(seller, "approved");
        mockMvc.perform(post("/api/admin/auctions/" + live + "/approve")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk());
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour', "
                + "end_time = now() + interval '10 seconds' WHERE id = ?", live);
        mockMvc.perform(post("/api/auctions/" + live + "/bids")
                        .header("Authorization", "Bearer " + buyer.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000000}"))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?", live);
        assertThat(auctions.endDueAuctions(Instant.now(), 200)).isGreaterThanOrEqualTo(1);
        UUID payment = jdbc.queryForObject("SELECT id FROM payments WHERE auction_id = ?", UUID.class, live);
        mockMvc.perform(post("/api/payments/" + payment + "/pay")
                        .header("Authorization", "Bearer " + buyer.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isOk());

        UUID rejected = auction(seller, "rejected");
        mockMvc.perform(post("/api/admin/auctions/" + rejected + "/reject")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Incomplete details\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/" + target.id() + "/block")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + target.token()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"supersecret"}
                """.formatted(users.findById(target.id()).orElseThrow().email())))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/users/" + target.id() + "/block")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":true}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs WHERE action = 'USER_BLOCKED' AND entity_id = ?
                """, Integer.class, target.id())).isEqualTo(1);
        mockMvc.perform(patch("/api/admin/users/" + target.id() + "/block")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + target.token()))
                .andExpect(status().isOk());

        assertThat(actions()).contains(
                "USER_LOGIN", "AUCTION_CREATED", "AUCTION_APPROVED", "AUCTION_REJECTED",
                "BID_PLACED", "AUCTION_EXTENDED", "AUCTION_ENDED", "PAYMENT_SUCCESS",
                "USER_BLOCKED", "USER_UNBLOCKED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs
                 WHERE user_id = ? AND action = 'AUCTION_APPROVED' AND entity_id = ?
                   AND old_value = 'PENDING_APPROVAL' AND new_value = 'SCHEDULED'
                """, Integer.class, admin.id(), live)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_logs
                 WHERE user_id = ? AND action = 'USER_LOGIN' AND ip_address = '203.0.113.7'
                """, Integer.class, buyer.id())).isEqualTo(1);

        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + buyer.token()))
                .andExpect(status().isForbidden());
        String body = mockMvc.perform(get("/api/admin/audit-logs?page=1&size=2")
                        .header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode page = json.readTree(body).get("data");
        assertThat(page.get("items").size()).isEqualTo(2);
        assertThat(page.get("page").intValue()).isEqualTo(1);
        assertThat(page.get("pageSize").intValue()).isEqualTo(2);
        assertThat(page.get("totalItems").longValue()).isGreaterThanOrEqualTo(9);
        assertThat(page.get("items").get(0).get("actorDisplayName").asString()).isNotBlank();
        assertThat(page.get("items").get(0).get("createdAt").asString()).isNotBlank();
    }

    @Test
    void rolledBackActionsAndFailedLoginsLeaveNoAuditEntry() throws Exception {
        Account user = account("rollback", RoleName.BUYER);
        int before = jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE user_id = ? AND action = 'USER_LOGIN'",
                Integer.class, user.id());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"nobody@example.com","password":"wrongpassword"}
                """)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_logs WHERE user_id = ? AND action = 'USER_LOGIN'",
                Integer.class, user.id())).isEqualTo(before);

        UUID rolledBack = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            events.publishEvent(new AuctionAuditEvent(AuctionAuditEvent.Action.CREATED,
                    rolledBack, user.id(), null, AuctionStatus.DRAFT));
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE entity_id = ?",
                Integer.class, rolledBack)).isZero();
    }
}
