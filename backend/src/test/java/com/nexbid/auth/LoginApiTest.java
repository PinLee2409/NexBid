package com.nexbid.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexbid.support.PostgresTestcontainer;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The four cases the guide names, plus proof the token actually opens a protected route.
 * VI: Bốn ca guide yêu cầu, cộng thêm bằng chứng token thật sự mở được một route cần xác thực.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class LoginApiTest {

    private static final String EMAIL = "login.user@nexbid.com";
    private static final String PASSWORD = "supersecret";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void registerTheAccount() throws Exception {
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (SELECT id FROM users WHERE LOWER(email) = ?)", EMAIL);
        jdbc.update("DELETE FROM users WHERE LOWER(email) = ?", EMAIL);

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Login User","email":"%s","password":"%s"}
                        """.formatted(EMAIL, PASSWORD)));
    }

    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void correctAccountGetsATokenAndItsOwner() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(EMAIL))
                .andExpect(jsonPath("$.data.user.roles[0]").value("BUYER"))
                // EN: The hash must never travel with the response.
                // VI: Hash mật khẩu tuyệt đối không được đi kèm response.
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist());
    }

    @Test
    void wrongPasswordFails() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password"}
                                """.formatted(EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void unknownEmailFailsIdenticallyToAWrongPassword() throws Exception {
        String unknown = login("nobody@nexbid.com", PASSWORD);
        String wrongPw = login(EMAIL, "wrong-password");

        JsonNode a = new com.fasterxml.jackson.databind.ObjectMapper().readTree(unknown);
        JsonNode b = new com.fasterxml.jackson.databind.ObjectMapper().readTree(wrongPw);

        // EN: Same code and same message, so the endpoint cannot be used to discover accounts.
        // VI: Cùng mã và cùng lời nhắn, để endpoint này không thể dùng để dò tài khoản.
        assertThat(a.get("code").asText()).isEqualTo(b.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(a.get("message").asText()).isEqualTo(b.get("message").asText());
    }

    @Test
    void blockedUserCannotSignIn() throws Exception {
        jdbc.update("UPDATE users SET status = 'BLOCKED' WHERE LOWER(email) = ?", EMAIL);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
    }

    @Test
    void theTokenOpensAProtectedRoute() throws Exception {
        String bodyText = login(EMAIL, PASSWORD);
        String token = objectMapper.readTree(bodyText).get("data").get("accessToken").asString();

        // EN: Without the token the same route is refused. /api/auctions is public from function 18,
        //     so the example here is a route that genuinely still needs a caller.
        // VI: Không có token thì chính route đó bị từ chối. /api/auctions đã công khai từ chức năng 18,
        //     nên ví dụ ở đây dùng một route thật sự vẫn cần biết người gọi là ai.
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));

        // EN: With it the request is authenticated and the profile comes back.
        // VI: Có token thì request được xác thực và hồ sơ trả về.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL));
    }

    @Test
    void aTamperedTokenIsIgnored() throws Exception {
        String bodyText = login(EMAIL, PASSWORD);
        String token = objectMapper.readTree(bodyText).get("data").get("accessToken").asString();

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
    }
}
