package com.nexbid.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.nexbid.auth.refresh.RefreshCookies;
import com.nexbid.auth.refresh.RefreshTokenService;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * EN: Refresh tokens (spec §7.2): the cookie, rotation, replay detection, signing out and blocking.
 * VI: Refresh token (spec §7.2): cookie, xoay vòng token, phát hiện dùng lại, đăng xuất và khoá tài khoản.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class RefreshTokenApiTest {

    private static final String PASSWORD = "supersecret";
    private static final Pattern COOKIE_VALUE = Pattern.compile(RefreshCookies.NAME + "=([^;]*)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenService refreshTokens;

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Refresh User","email":"%s","password":"%s"}
                """.formatted(email, PASSWORD)));
    }

    private MvcResult login(String email, Boolean rememberMe) throws Exception {
        String remember = rememberMe == null ? "" : ",\"rememberMe\":" + rememberMe;
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s"%s}
                        """.formatted(email, PASSWORD, remember)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String setCookie(MvcResult result) {
        return result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    }

    private static String cookieValue(MvcResult result) {
        Matcher matcher = COOKIE_VALUE.matcher(setCookie(result));
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private MvcResult refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookies.NAME, token))).andReturn();
    }

    private String accessToken(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("accessToken").asString();
    }

    private UUID idOf(String email) {
        return users.findByEmail(email).orElseThrow().id();
    }

    @Test
    void signingInSetsACookieScriptsCannotReadAndOnlyTheAuthEndpointsReceive() throws Exception {
        register("refresh.cookie@nexbid.com");
        MvcResult result = login("refresh.cookie@nexbid.com", null);

        String header = setCookie(result);
        assertThat(header).contains(RefreshCookies.NAME + "=", "HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth");
        // EN: Seven days, give or take the moment the request took. / VI: Bảy ngày, xê dịch bằng thời gian request vừa chạy.
        Matcher maxAge = Pattern.compile("Max-Age=(\\d+)").matcher(header);
        assertThat(maxAge.find()).isTrue();
        assertThat(Long.parseLong(maxAge.group(1))).isBetween(Duration.ofDays(7).minusSeconds(5).toSeconds(), Duration.ofDays(7).toSeconds());
        // EN: The access token itself is now short-lived. / VI: Bản thân access token giờ sống rất ngắn.
        Instant expiresAt = Instant.parse(
                objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("expiresAt").asString());
        assertThat(expiresAt).isBetween(Instant.now().plus(Duration.ofMinutes(14)), Instant.now().plus(Duration.ofMinutes(16)));
    }

    @Test
    void withoutRememberMeTheCookieLastsUntilTheBrowserCloses() throws Exception {
        register("refresh.session@nexbid.com");
        String header = setCookie(login("refresh.session@nexbid.com", false));

        assertThat(header).contains(RefreshCookies.NAME + "=").doesNotContain("Max-Age").doesNotContain("Expires");
    }

    @Test
    void aRefreshGivesANewAccessTokenAndReplacesTheCookie() throws Exception {
        register("refresh.rotate@nexbid.com");
        String first = cookieValue(login("refresh.rotate@nexbid.com", null));

        MvcResult renewed = refresh(first);
        assertThat(renewed.getResponse().getStatus()).isEqualTo(200);
        String second = cookieValue(renewed);
        assertThat(second).isNotBlank().isNotEqualTo(first);
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken(renewed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("refresh.rotate@nexbid.com"));

        // EN: Each token works once. / VI: Mỗi token chỉ dùng được một lần.
        mockMvc.perform(post("/api/auth/refresh").cookie(new Cookie(RefreshCookies.NAME, first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void aReplayedTokenEndsTheSessionForWhoeverHoldsTheNewOneToo() throws Exception {
        register("refresh.replay@nexbid.com");
        String stolen = cookieValue(login("refresh.replay@nexbid.com", null));
        String owners = cookieValue(refresh(stolen));

        MvcResult replay = refresh(stolen);
        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        // EN: The refused cookie is removed from the browser. / VI: Cookie bị từ chối được xoá khỏi trình duyệt.
        assertThat(setCookie(replay)).contains("Max-Age=0");

        assertThat(refresh(owners).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void signingOutEndsThatSessionOnly() throws Exception {
        register("refresh.logout@nexbid.com");
        String phone = cookieValue(login("refresh.logout@nexbid.com", null));
        String laptop = cookieValue(login("refresh.logout@nexbid.com", null));

        MvcResult signedOut = mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(RefreshCookies.NAME, phone)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(setCookie(signedOut)).contains("Max-Age=0");

        assertThat(refresh(phone).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(laptop).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void blockingAnAccountEndsEverySessionItHas() throws Exception {
        register("refresh.warden@nexbid.com");
        users.grantRole("refresh.warden@nexbid.com", RoleName.ADMIN);
        String admin = accessToken(login("refresh.warden@nexbid.com", null));
        register("refresh.blocked@nexbid.com");
        String phone = cookieValue(login("refresh.blocked@nexbid.com", null));
        String laptop = cookieValue(login("refresh.blocked@nexbid.com", false));
        UUID buyer = idOf("refresh.blocked@nexbid.com");

        mockMvc.perform(patch("/api/admin/users/" + buyer + "/block").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":true}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM refresh_tokens WHERE user_id = ? AND revoked_at IS NULL", Integer.class, buyer))
                .isZero();

        // EN: Revoked, not merely refused while blocked: unblocking does not bring the sessions back.
        // VI: Bị thu hồi hẳn, không chỉ bị từ chối khi đang khoá: mở khoá cũng không làm các phiên sống lại.
        mockMvc.perform(patch("/api/admin/users/" + buyer + "/block").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":false}"))
                .andExpect(status().isOk());
        assertThat(refresh(phone).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh(laptop).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void anExpiredTokenIsRefusedAndPrunedAway() throws Exception {
        register("refresh.expired@nexbid.com");
        String token = cookieValue(login("refresh.expired@nexbid.com", null));
        UUID user = idOf("refresh.expired@nexbid.com");
        jdbc.update("UPDATE refresh_tokens SET expires_at = now() - interval '1 minute' WHERE user_id = ?", user);

        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(401);

        refreshTokens.pruneExpired();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, user))
                .isZero();
    }

    @Test
    void withoutACookieThereIsNoSessionToRenewOrEnd() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isOk());
    }

    @Test
    void onlyAHashOfTheTokenIsStored() throws Exception {
        register("refresh.hash@nexbid.com");
        String token = cookieValue(login("refresh.hash@nexbid.com", null));

        String stored = jdbc.queryForObject(
                "SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String.class, idOf("refresh.hash@nexbid.com"));
        assertThat(stored).hasSize(64).isNotEqualTo(token).doesNotContain(token);
    }
}
