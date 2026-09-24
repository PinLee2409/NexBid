package com.nexbid.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.PostgresTestcontainer;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Profile (guide §9) — read and rename yourself, and nobody else.
 * VI: Hồ sơ (guide §9) — đọc và đổi tên của chính mình, không phải của ai khác.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class ProfileApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email, String fullName) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","email":"%s","password":"supersecret"}
                        """.formatted(fullName, email)));

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    @Test
    void readsTheSignedInAccount() throws Exception {
        String token = tokenFor("profile.read@nexbid.com", "Profile Reader");

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("profile.read@nexbid.com"))
                .andExpect(jsonPath("$.data.fullName").value("Profile Reader"))
                .andExpect(jsonPath("$.data.roles[0]").value("BUYER"))
                // EN: The account view has no password field at all.
                // VI: Bản xem tài khoản hoàn toàn không có field mật khẩu.
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void renamesTheSignedInAccount() throws Exception {
        String token = tokenFor("profile.edit@nexbid.com", "Old Name");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"New Name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("New Name"));

        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.fullName").value("New Name"));
    }

    @Test
    void roleAndStatusCannotBeChangedByTheUser() throws Exception {
        String token = tokenFor("profile.escalate@nexbid.com", "Climber");

        // EN: The body asks for ADMIN and a different id. The request type has no such fields,
        //     so they are read as nothing at all — not blocked, simply absent.
        // VI: Body xin quyền ADMIN và một id khác. Kiểu dữ liệu request không có những field đó,
        //     nên chúng bị bỏ qua hoàn toàn — không phải bị chặn, mà là không tồn tại.
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Climber","roles":["ADMIN"],"status":"BLOCKED",
                                 "id":"00000000-0000-0000-0000-000000000001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("BUYER"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.id").value(org.hamcrest.Matchers.not(
                        "00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void oneUserCannotSeeAnother() throws Exception {
        String aliceToken = tokenFor("alice.profile@nexbid.com", "Alice");
        tokenFor("bob.profile@nexbid.com", "Bob");

        // EN: There is no way to name whose profile to read — the token decides, so Alice always gets Alice.
        // VI: Không có cách nào chỉ định đọc hồ sơ của ai — token quyết định, nên Alice luôn chỉ nhận về Alice.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + aliceToken))
                .andExpect(jsonPath("$.data.email").value("alice.profile@nexbid.com"));
    }

    @Test
    void blankNameIsRejected() throws Exception {
        String token = tokenFor("profile.blank@nexbid.com", "Someone");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.fullName").exists());
    }

    @Test
    void withoutATokenThereIsNoProfile() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
    }
}
