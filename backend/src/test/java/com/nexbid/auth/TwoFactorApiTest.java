package com.nexbid.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.TestInfrastructure;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * EN: Two-step sign-in: set up with the app's first code, sign in with password then code, recovery codes work once,
 *     and the challenge is no access token.
 * VI: Đăng nhập hai bước: cài đặt bằng mã đầu tiên của ứng dụng, đăng nhập bằng mật khẩu rồi mã, mã khôi phục dùng được
 *     một lần, và challenge không phải access token.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class TwoFactorApiTest {

    private static final String EMAIL = "two.step@nexbid.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"supersecret\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private org.springframework.test.web.servlet.ResultActions withCode(String challenge, String code) throws Exception {
        return mockMvc.perform(post("/api/auth/login/2fa").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challenge\":\"" + challenge + "\",\"code\":\"" + code + "\"}"));
    }

    @Test
    void signingInTakesTheAppsCodeOnceItIsOn() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Two Step\",\"email\":\"" + EMAIL + "\",\"password\":\"supersecret\"}"));
        String token = login().get("accessToken").asString();
        String bearer = "Bearer " + token;

        mockMvc.perform(get("/api/users/me/2fa").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.enabled").value(false));
        mockMvc.perform(post("/api/users/me/2fa/enable").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_NOT_SET_UP"));
        String setup = mockMvc.perform(post("/api/users/me/2fa/setup").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpauthUri").value(org.hamcrest.Matchers.startsWith(
                        "otpauth://totp/NexBid%3Atwo.step%40nexbid.com?secret=")))
                .andReturn().getResponse().getContentAsString();
        String secret = objectMapper.readTree(setup).get("data").get("secret").asString();
        assertThat(jdbc.queryForObject("SELECT secret FROM user_totp", String.class)).doesNotContain(secret);

        long now = Totp.stepAt(Instant.now());
        mockMvc.perform(post("/api/users/me/2fa/enable").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        String enabled = mockMvc.perform(post("/api/users/me/2fa/enable").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + Totp.code(secret, now) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recoveryCodes.length()").value(8))
                .andReturn().getResponse().getContentAsString();
        String recovery = objectMapper.readTree(enabled).get("data").get("recoveryCodes").get(0).asString();
        mockMvc.perform(post("/api/users/me/2fa/setup").header("Authorization", bearer))
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_ALREADY_ON"));

        // EN: The password step hands back a challenge, no token and no cookie. / VI: Bước mật khẩu trả về challenge, không token, không cookie.
        String challenge = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"supersecret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.data.accessToken").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        challenge = objectMapper.readTree(challenge).get("data").get("twoFactorChallenge").asString();
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + challenge))
                .andExpect(status().isUnauthorized());

        // EN: The code that turned it on is spent; the next one works. / VI: Mã đã dùng để bật thì hết hiệu lực; mã kế tiếp dùng được.
        withCode(challenge, Totp.code(secret, now)).andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        withCode(challenge + "x", Totp.code(secret, now + 1))
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CHALLENGE_EXPIRED"));
        withCode(challenge, Totp.code(secret, now + 1))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        String again = login().get("twoFactorChallenge").asString();
        withCode(again, recovery.toLowerCase()).andExpect(status().isOk());
        String once = login().get("twoFactorChallenge").asString();
        withCode(once, recovery).andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));

        mockMvc.perform(post("/api/users/me/2fa/disable").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"999999\"}"))
                .andExpect(jsonPath("$.code").value("TWO_FACTOR_CODE_INVALID"));
        String second = objectMapper.readTree(enabled).get("data").get("recoveryCodes").get(1).asString();
        mockMvc.perform(post("/api/users/me/2fa/disable").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + second + "\"}"))
                .andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(login().get("accessToken").asString()).isNotBlank();
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE action LIKE 'TWO_FACTOR_%' ORDER BY created_at",
                String.class)).containsExactly("TWO_FACTOR_ENABLED", "TWO_FACTOR_DISABLED");
    }

    @Test
    void codesFollowTheRfcExample() {
        // EN: RFC 6238 appendix B, SHA-1, at 59 seconds: 94287082 → its last six digits. / VI: Phụ lục B của RFC 6238, SHA-1, tại giây 59.
        String secret = Totp.base32("12345678901234567890".getBytes());
        assertThat(Totp.code(secret, 1)).isEqualTo("287082");
        assertThat(new String(Totp.unbase32(secret))).isEqualTo("12345678901234567890");
        assertThat(Totp.match(secret, "287082", Instant.ofEpochSecond(59), 0)).isEqualTo(1);
        assertThat(Totp.match(secret, "287082", Instant.ofEpochSecond(59), 1)).isEqualTo(-1);
        assertThat(Totp.match(secret, "28708x", Instant.ofEpochSecond(59), 0)).isEqualTo(-1);
    }
}
