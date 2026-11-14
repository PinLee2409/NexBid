package com.nexbid.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Admins see the email outbox by state and send a failed email again.
 * VI: Admin xem hàng chờ email theo trạng thái và gửi lại email đã thất bại.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class AdminEmailApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

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

    private UUID email(String status) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO email_outbox (id, to_email, kind, subject, html, text, status, attempts, next_attempt_at,
                                          last_error, created_at)
                VALUES (?, 'someone@nexbid.com', 'WELCOME', 'Welcome', '<p>hi</p>', 'hi', ?, 6, ?, 'Brevo said no', ?)
                """, id, status, now, now);
        return id;
    }

    @Test
    void aFailedEmailCanBeSentAgainAndNothingElse() throws Exception {
        String admin = tokenFor("mail.admin@nexbid.com", "Mail Admin", RoleName.ADMIN);
        String buyer = tokenFor("mail.buyer@nexbid.com", "Mail Buyer", RoleName.BUYER);
        UUID failed = email("FAILED");
        UUID sent = email("SENT");

        mockMvc.perform(get("/api/admin/emails").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/emails").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.FAILED").isNumber())
                .andExpect(jsonPath("$.data.items[?(@.id == '" + failed + "')].lastError").value("Brevo said no"));
        mockMvc.perform(get("/api/admin/emails").param("status", "NOPE").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/emails/" + failed + "/retry").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status || ':' || attempts || ':' || (last_error IS NULL) FROM email_outbox WHERE id = ?",
                String.class, failed)).isEqualTo("PENDING:0:true");

        mockMvc.perform(post("/api/admin/emails/" + failed + "/retry").header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_NOT_RETRYABLE"));
        mockMvc.perform(post("/api/admin/emails/" + sent + "/retry").header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/admin/emails/" + UUID.randomUUID() + "/retry").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }
}
