package com.nexbid.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.PostgresTestcontainer;

/**
 * EN: The whole registration path end to end — HTTP, validation, hashing, database.
 * VI: Toàn bộ luồng đăng ký từ đầu tới cuối — HTTP, validation, hash mật khẩu, database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class RegisterApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static String body(String fullName, String email, String password) {
        return """
                {"fullName":"%s","email":"%s","password":"%s"}
                """.formatted(fullName, email, password);
    }

    @Test
    void registersABuyerAndNeverReturnsThePassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Pin Le", "new.buyer@nexbid.com", "supersecret")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.email").value("new.buyer@nexbid.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("BUYER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    void storesAHashNeverThePlainPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("Hash Check", "hash.check@nexbid.com", "supersecret")));

        // EN: Read the column itself. Anything other than a hash here is the bug this test exists for.
        // VI: Đọc thẳng cột trong DB. Nếu chỗ này không phải hash thì đúng là lỗi mà test này sinh ra để bắt.
        String stored = jdbc.queryForObject(
                "SELECT password FROM users WHERE LOWER(email) = ?",
                String.class, "hash.check@nexbid.com");

        assertThat(stored).isNotEqualTo("supersecret");
        assertThat(stored).startsWith("$2");
        assertThat(passwordEncoder.matches("supersecret", stored)).isTrue();
    }

    @Test
    void duplicateEmailIsRefusedWhateverTheCase() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("First", "dupe@nexbid.com", "supersecret")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Second", "DUPE@NEXBID.COM", "supersecret")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void invalidEmailIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Pin Le", "not-an-email", "supersecret")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Pin Le", "short.pw@nexbid.com", "1234567")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.password").exists());
    }

    @Test
    void registrationNeedsNoToken() throws Exception {
        // EN: No credentials sent at all — this must not come back 401.
        // VI: Không gửi thông tin đăng nhập nào — không được trả về 401.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Anon", "anon@nexbid.com", "supersecret")))
                .andExpect(status().isCreated());
    }
}
