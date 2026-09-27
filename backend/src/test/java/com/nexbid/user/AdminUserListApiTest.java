package com.nexbid.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.nexbid.support.TestInfrastructure;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: `GET /api/admin/users` (spec §27): the admin console's account list — search, status filter, admins only.
 * VI: `GET /api/admin/users` (spec §27): danh sách tài khoản của trang quản trị — tìm kiếm, lọc trạng thái, chỉ admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class AdminUserListApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String email, String name) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"%s","email":"%s","password":"supersecret"}
                """.formatted(name, email)));
        return users.findByEmail(email).orElseThrow().id().toString();
    }

    private String tokenFor(String email, String name, RoleName role) throws Exception {
        register(email, name);
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"supersecret"}
                        """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    @Test
    void anAdminFindsAccountsByNameOrEmailAndByStatus() throws Exception {
        String admin = tokenFor("userlist.admin@nexbid.com", "Userlist Admin", RoleName.ADMIN);
        String quinn = register("userlist.quinn@nexbid.com", "Quinn Zebrafish");
        String rory = register("userlist.rory@nexbid.com", "Rory Zebrafish");
        mockMvc.perform(patch("/api/admin/users/" + rory + "/block").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":true}"));

        mockMvc.perform(get("/api/admin/users").param("search", "zebrafish").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.items[?(@.id == '" + quinn + "')].email").value("userlist.quinn@nexbid.com"));

        mockMvc.perform(get("/api/admin/users").param("search", "USERLIST.RORY@").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].status").value("BLOCKED"));

        mockMvc.perform(get("/api/admin/users").param("search", "zebrafish").param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(quinn));
    }

    @Test
    void noAdminCanBeBlockedNotEvenByThemselves() throws Exception {
        String admin = tokenFor("userlist.boss@nexbid.com", "Boss Admin", RoleName.ADMIN);
        tokenFor("userlist.peer@nexbid.com", "Peer Admin", RoleName.ADMIN);
        String self = users.findByEmail("userlist.boss@nexbid.com").orElseThrow().id().toString();
        String peer = users.findByEmail("userlist.peer@nexbid.com").orElseThrow().id().toString();

        for (String target : java.util.List.of(self, peer)) {
            mockMvc.perform(patch("/api/admin/users/" + target + "/block").header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":true}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ADMIN_NOT_BLOCKABLE"));
        }
        assertThat(users.findByEmail("userlist.peer@nexbid.com").orElseThrow().status()).isEqualTo(UserStatus.ACTIVE);

        // EN: Unblocking is never refused — it can only give access back. / VI: Mở khoá không bao giờ bị từ chối — nó chỉ trả lại quyền truy cập.
        mockMvc.perform(patch("/api/admin/users/" + peer + "/block").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void aBlockedAccountsTokenStopsWorkingOnTheVeryNextRequest() throws Exception {
        String admin = tokenFor("userlist.warden@nexbid.com", "Warden Admin", RoleName.ADMIN);
        String buyer = tokenFor("userlist.cached@nexbid.com", "Cached Buyer", RoleName.BUYER);
        String buyerId = users.findByEmail("userlist.cached@nexbid.com").orElseThrow().id().toString();

        // EN: The first request remembers the account as active. / VI: Request đầu tiên ghi nhớ tài khoản đang hoạt động.
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + buyer)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/" + buyerId + "/block").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/admin/users/" + buyerId + "/block").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"blocked\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + buyer)).andExpect(status().isOk());
    }

    @Test
    void onlyAdminsMayListAccounts() throws Exception {
        String buyer = tokenFor("userlist.buyer@nexbid.com", "Userlist Buyer", RoleName.BUYER);

        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }
}
