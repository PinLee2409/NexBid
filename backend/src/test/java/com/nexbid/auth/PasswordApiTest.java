package com.nexbid.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nexbid.support.TestInfrastructure;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Forgotten and changed passwords: the reset form never says whether an email has an account, a link works once, and
 *     a new password ends the old sessions and is announced by email.
 * VI: Quên và đổi mật khẩu: form đặt lại không bao giờ cho biết email có tài khoản hay không, link chỉ dùng một lần, và
 *     mật khẩu mới kết thúc các phiên cũ và được báo qua email.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class PasswordApiTest {

    private static final Pattern LINK = Pattern.compile("reset-password\\?token=([A-Za-z0-9_-]+)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Pass Word","email":"%s","password":"supersecret"}""".formatted(email)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)));
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions reset(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"));
    }

    private String latestToken(String email) {
        String text = jdbc.queryForObject("""
                SELECT o.html || ' ' || o.text FROM email_outbox o JOIN users u ON u.id = o.user_id
                WHERE u.email = ? AND o.kind = 'PASSWORD_RESET' ORDER BY o.created_at DESC LIMIT 1
                """, String.class, email);
        Matcher found = LINK.matcher(text);
        assertThat(found.find()).isTrue();
        return found.group(1);
    }

    @Test
    void aResetLinkWorksOnceAndEndsTheOldSessions() throws Exception {
        String email = "forgot.me@nexbid.com";
        register(email);
        String refresh = login(email, "supersecret").andReturn().getResponse().getHeader("Set-Cookie");

        String answer = forgot("nobody.here@nexbid.com").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(forgot(email).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .isEqualTo(answer);
        String token = latestToken(email);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM password_resets WHERE token_hash = ?", Integer.class,
                token)).isZero();

        reset(token, "short").andExpect(status().isBadRequest());
        reset(token, "a-brand-new-one").andExpect(status().isOk());
        reset(token, "another-new-one").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_INVALID"));
        reset("made-up-token", "another-new-one").andExpect(jsonPath("$.code").value("PASSWORD_RESET_INVALID"));

        login(email, "supersecret").andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login(email, "a-brand-new-one").andExpect(status().isOk());
        // EN: The session from before the reset is over. / VI: Phiên từ trước khi đặt lại đã kết thúc.
        mockMvc.perform(post("/api/auth/refresh").header("Cookie", refresh.split(";")[0]))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM email_outbox o JOIN users u ON u.id = o.user_id
                WHERE u.email = ? AND o.kind = 'PASSWORD_CHANGED'
                """, Integer.class, email)).isOne();
    }

    /**
     * EN: A fourth request still sends a link; the oldest open one stops working instead.
     * VI: Yêu cầu thứ tư vẫn được gửi link; link cũ nhất còn hạn mất hiệu lực thay vào đó.
     */
    @Test
    void aFourthRequestRetiresTheOldestLink() throws Exception {
        String email = "many.links@nexbid.com";
        register(email);
        forgot(email);
        String first = latestToken(email);
        forgot(email);
        forgot(email);
        forgot(email).andExpect(status().isOk());
        String fourth = latestToken(email);
        assertThat(fourth).isNotEqualTo(first);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM password_resets r JOIN users u ON u.id = r.user_id
                WHERE u.email = ? AND r.used_at IS NULL
                """, Integer.class, email)).isEqualTo(3);
        reset(first, "first-is-gone").andExpect(jsonPath("$.code").value("PASSWORD_RESET_INVALID"));
        reset(fourth, "the-newest-works").andExpect(status().isOk());
    }

    @Test
    void aSignedInAccountChangesItsPasswordAndStaysSignedInHere() throws Exception {
        String email = "change.me@nexbid.com";
        register(email);
        String token = objectMapper.readTree(login(email, "supersecret").andReturn().getResponse().getContentAsString())
                .get("data").get("accessToken").asString();
        String change = "/api/users/me/password";
        mockMvc.perform(post(change).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-one\",\"newPassword\":\"brand-new-pass\"}"))
                .andExpect(jsonPath("$.code").value("PASSWORD_INCORRECT"));
        mockMvc.perform(post(change).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"supersecret\",\"newPassword\":\"supersecret\"}"))
                .andExpect(jsonPath("$.code").value("PASSWORD_UNCHANGED"));
        mockMvc.perform(post(change).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"supersecret\",\"newPassword\":\"brand-new-pass\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"));
        login(email, "brand-new-pass").andExpect(status().isOk());
        mockMvc.perform(post(change).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"brand-new-pass\",\"newPassword\":\"newer-pass-1\"}"))
                .andExpect(status().isUnauthorized());
    }
}
