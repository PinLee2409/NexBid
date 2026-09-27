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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nexbid.support.TestInfrastructure;

/**
 * EN: Password guessing is slowed down (5 wrong per email and address, 20 per address, in 15 minutes). Each
 *     test signs in from its own address, so their counts never meet.
 * VI: Việc dò mật khẩu bị làm chậm (5 lần sai cho mỗi email và địa chỉ, 20 lần cho mỗi địa chỉ, trong 15 phút).
 *     Mỗi test đăng nhập từ một địa chỉ riêng, nên bộ đếm của chúng không bao giờ gặp nhau.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class LoginRateLimitTest {

    private static final String PASSWORD = "supersecret";

    @Autowired
    private MockMvc mockMvc;

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Guarded User","email":"%s","password":"%s"}
                """.formatted(email, PASSWORD)));
    }

    private ResultActions login(String email, String password, String address) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(address);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    @Test
    void fiveWrongPasswordsStopThatEmailFromThatAddressOnly() throws Exception {
        register("guard.locked@nexbid.com");
        for (int i = 0; i < 5; i++) {
            login("guard.locked@nexbid.com", "wrong-" + i, "203.0.113.10").andExpect(status().isUnauthorized());
        }

        // EN: Even the right password is refused now, without saying whether it was right.
        // VI: Giờ cả mật khẩu đúng cũng bị từ chối, và không cho biết nó có đúng hay không.
        String retryAfter = login("guard.locked@nexbid.com", PASSWORD, "203.0.113.10")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andReturn().getResponse().getHeader("Retry-After");
        assertThat(Long.parseLong(retryAfter)).isBetween(1L, 15 * 60L);

        // EN: The owner, somewhere else, is not locked out. / VI: Chủ tài khoản ở nơi khác không bị khoá.
        login("guard.locked@nexbid.com", PASSWORD, "203.0.113.11").andExpect(status().isOk());
    }

    @Test
    void aCorrectPasswordForgivesTheTyposBeforeIt() throws Exception {
        register("guard.typos@nexbid.com");
        for (int i = 0; i < 4; i++) {
            login("guard.typos@nexbid.com", "typo-" + i, "203.0.113.20").andExpect(status().isUnauthorized());
        }
        login("guard.typos@nexbid.com", PASSWORD, "203.0.113.20").andExpect(status().isOk());

        for (int i = 0; i < 4; i++) {
            login("guard.typos@nexbid.com", "typo-again-" + i, "203.0.113.20").andExpect(status().isUnauthorized());
        }
        login("guard.typos@nexbid.com", PASSWORD, "203.0.113.20").andExpect(status().isOk());
    }

    @Test
    void oneAddressCannotWorkThroughManyAccounts() throws Exception {
        for (int i = 0; i < 20; i++) {
            login("guard.spray" + i + "@nexbid.com", "guess", "203.0.113.30").andExpect(status().isUnauthorized());
        }
        register("guard.target@nexbid.com");

        login("guard.target@nexbid.com", PASSWORD, "203.0.113.30")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
        login("guard.target@nexbid.com", PASSWORD, "203.0.113.31").andExpect(status().isOk());
    }
}
