package com.nexbid.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nexbid.support.TestInfrastructure;
import com.sun.net.httpserver.HttpServer;

import io.jsonwebtoken.Jwts;

/**
 * EN: Google sign-in against keys served locally: a new email makes a buyer, the same Google account comes back to it,
 *     and tokens for another site, unverified emails or unknown keys are refused.
 * VI: Đăng nhập Google với khoá phục vụ tại chỗ: email mới tạo người mua, cùng tài khoản Google quay lại đúng người đó,
 *     và token cho site khác, email chưa xác minh hay khoá lạ đều bị từ chối.
 */
@SpringBootTest(properties = {"nexbid.scheduler.enabled=false", "nexbid.auth.google.client-id=test-client"})
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class GoogleSignInApiTest {

    private static final KeyPair KEYS = Jwts.SIG.RS256.keyPair().build();
    private static final KeyPair STRANGER = Jwts.SIG.RS256.keyPair().build();
    private static final HttpServer JWKS = serveKeys();

    @DynamicPropertySource
    static void keysUrl(DynamicPropertyRegistry registry) {
        registry.add("nexbid.auth.google.keys-url", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/certs");
    }

    private static HttpServer serveKeys() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            RSAPublicKey key = (RSAPublicKey) KEYS.getPublic();
            String body = """
                    {"keys":[{"kty":"RSA","kid":"k1","alg":"RS256","use":"sig","n":"%s","e":"%s"}]}
                    """.formatted(url(key.getModulus()), url(key.getPublicExponent()));
            server.createContext("/certs", exchange -> {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private static String idToken(KeyPair keys, String audience, String subject, String email, Object verified) {
        Instant now = Instant.now();
        return Jwts.builder().header().keyId("k1").and()
                .issuer("https://accounts.google.com").audience().add(audience).and()
                .subject(subject).claims(Map.of("email", email, "email_verified", verified, "name", "Gina Google"))
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(600)))
                .signWith(keys.getPrivate()).compact();
    }

    private ResultActions signIn(String credential) throws Exception {
        return mockMvc.perform(post("/api/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"credential\":\"" + credential + "\"}"));
    }

    @Test
    void aVerifiedGoogleAccountSignsInAndIsRememberedBySubject() throws Exception {
        mockMvc.perform(get("/api/auth/providers")).andExpect(jsonPath("$.data.googleClientId").value("test-client"));

        signIn(idToken(KEYS, "test-client", "g-100", "Gina.G@Gmail.com", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("gina.g@gmail.com"))
                .andExpect(jsonPath("$.data.user.roles[0]").value("BUYER"));
        String userId = jdbc.queryForObject("SELECT user_id::text FROM user_identities WHERE subject = 'g-100'",
                String.class);
        // EN: The same Google account, under a changed email, is still the same person. / VI: Cùng tài khoản Google, dù email đã đổi, vẫn là cùng một người.
        signIn(idToken(KEYS, "test-client", "g-100", "gina.new@gmail.com", "true"))
                .andExpect(jsonPath("$.data.user.id").value(userId));

        // EN: An existing password account with that email is linked, not duplicated. / VI: Tài khoản mật khẩu có sẵn cùng email được liên kết, không tạo trùng.
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Pat Password\",\"email\":\"pat@nexbid.com\",\"password\":\"supersecret\"}"));
        String patId = jdbc.queryForObject("SELECT id::text FROM users WHERE email = 'pat@nexbid.com'", String.class);
        signIn(idToken(KEYS, "test-client", "g-200", "pat@nexbid.com", true))
                .andExpect(jsonPath("$.data.user.id").value(patId));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'pat@nexbid.com'", Integer.class))
                .isOne();
        // EN: Whoever registered that email first keeps no way in: the old password stops working.
        // VI: Ai đăng ký email đó trước không còn đường vào: mật khẩu cũ hết dùng được.
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"pat@nexbid.com\",\"password\":\"supersecret\"}"))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        // EN: Coming back with Google later changes nothing more. / VI: Quay lại bằng Google sau đó không đổi gì thêm.
        signIn(idToken(KEYS, "test-client", "g-200", "pat@nexbid.com", true)).andExpect(status().isOk());

        signIn(idToken(KEYS, "another-site", "g-300", "x@gmail.com", true))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("GOOGLE_SIGN_IN_FAILED"));
        signIn(idToken(KEYS, "test-client", "g-301", "unverified@gmail.com", false))
                .andExpect(jsonPath("$.code").value("GOOGLE_SIGN_IN_FAILED"));
        signIn(idToken(STRANGER, "test-client", "g-302", "forged@gmail.com", true))
                .andExpect(jsonPath("$.code").value("GOOGLE_SIGN_IN_FAILED"));
        signIn("not-a-token").andExpect(jsonPath("$.code").value("GOOGLE_SIGN_IN_FAILED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email IN ('x@gmail.com', 'forged@gmail.com')",
                Integer.class)).isZero();
    }
}
