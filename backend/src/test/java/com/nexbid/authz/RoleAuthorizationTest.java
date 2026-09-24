package com.nexbid.authz;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The rule guide §8 asks for — a BUYER must be refused on /api/admin/**, an ADMIN must get through.
 * VI: Đúng luật guide §8 yêu cầu — BUYER phải bị từ chối ở /api/admin/**, ADMIN phải đi qua được.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestInfrastructure.class, ProtectedProbeController.class })
class RoleAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private ObjectMapper objectMapper;

    /** EN: Registers, optionally promotes, and returns a token. / VI: Đăng ký, cấp quyền nếu cần, trả về token. */
    private String tokenFor(String email, RoleName extraRole) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Role Test","email":"%s","password":"supersecret"}
                        """.formatted(email)));

        if (extraRole != null) {
            users.grantRole(email, extraRole);
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    @Test
    void buyerIsRefusedFromTheAdminArea() throws Exception {
        String token = tokenFor("buyer.role@nexbid.com", null);

        mockMvc.perform(get("/api/admin/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void adminGetsThrough() throws Exception {
        String token = tokenFor("admin.role@nexbid.com", RoleName.ADMIN);

        mockMvc.perform(get("/api/admin/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("admin area"));
    }

    @Test
    void buyerIsRefusedFromTheSellerArea() throws Exception {
        String token = tokenFor("buyer2.role@nexbid.com", null);

        mockMvc.perform(get("/api/seller/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void sellerGetsThrough() throws Exception {
        String token = tokenFor("seller.role@nexbid.com", RoleName.SELLER);

        mockMvc.perform(get("/api/seller/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminIsStillRefusedFromTheSellerArea() throws Exception {
        // EN: ADMIN is not a superset of SELLER — the roles are separate jobs (spec §4).
        // VI: ADMIN không bao hàm SELLER — hai vai trò là hai công việc khác nhau (spec §4).
        String token = tokenFor("admin2.role@nexbid.com", RoleName.ADMIN);

        mockMvc.perform(get("/api/seller/probe").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void noTokenIsRefusedBeforeTheRoleIsEvenChecked() throws Exception {
        mockMvc.perform(get("/api/admin/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
    }

    @Test
    void methodLevelSecurityAlsoApplies() throws Exception {
        String token = tokenFor("buyer3.role@nexbid.com", null);

        // EN: This path is not under /api/admin, so only @PreAuthorize is guarding it.
        // VI: Đường này không nằm dưới /api/admin, nên chỉ có @PreAuthorize canh giữ.
        mockMvc.perform(get("/api/probe/admin-only").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
