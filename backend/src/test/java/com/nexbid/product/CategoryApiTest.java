package com.nexbid.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Categories (guide §10) — public to read, ADMIN to change.
 * VI: Danh mục (guide §10) — ai cũng đọc được, chỉ ADMIN mới sửa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class CategoryApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email, RoleName extraRole) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Cat Test","email":"%s","password":"supersecret"}
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

    private String createCategory(String token, String json) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    @Test
    void anyoneCanBrowseWithoutSigningIn() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // EN: The seven the migration seeds, matching what the frontend already browses by.
                // VI: Bảy mục migration gieo sẵn, khớp với những gì frontend đang duyệt theo.
                .andExpect(jsonPath("$.data.length()").value(Matchers.greaterThanOrEqualTo(7)))
                .andExpect(jsonPath("$.data[?(@.slug == 'watches')].name").value("Watches"));
    }

    @Test
    void oneCategoryCanBeFetchedBySlug() throws Exception {
        mockMvc.perform(get("/api/categories/collectibles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Collectibles"));
    }

    @Test
    void anUnknownSlugIs404() throws Exception {
        mockMvc.perform(get("/api/categories/no-such-thing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void aBuyerCannotCreateCategories() throws Exception {
        String token = tokenFor("cat.buyer@nexbid.com", null);

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sneaky\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void anAdminCreatesOneAndTheSlugIsDerived() throws Exception {
        String token = tokenFor("cat.admin@nexbid.com", RoleName.ADMIN);

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rare Books\",\"description\":\"First editions.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("rare-books"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // EN: And it appears in the public list straight away.
        // VI: Và nó hiện ngay trong danh sách công khai.
        mockMvc.perform(get("/api/categories/rare-books"))
                .andExpect(status().isOk());
    }

    @Test
    void aTypedSlugIsStillCleaned() throws Exception {
        String token = tokenFor("cat.admin2@nexbid.com", RoleName.ADMIN);

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Vintage Audio\",\"slug\":\"Đồ Âm Thanh Cũ\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("do-am-thanh-cu"));
    }

    @Test
    void aDuplicateNameIsRefusedWhateverTheCase() throws Exception {
        String token = tokenFor("cat.admin3@nexbid.com", RoleName.ADMIN);

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"watches\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_EXISTS"));
    }

    @Test
    void archivingHidesItFromBrowsingButKeepsTheRow() throws Exception {
        String token = tokenFor("cat.admin4@nexbid.com", RoleName.ADMIN);
        String id = createCategory(token, "{\"name\":\"Temporary Shelf\"}");

        mockMvc.perform(put("/api/admin/categories/" + id + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        // EN: Gone from the public list, still there for an admin — nothing is ever deleted.
        // VI: Biến khỏi danh sách công khai, admin vẫn thấy — không có gì bị xoá đi cả.
        mockMvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$.data[?(@.slug == 'temporary-shelf')]").isEmpty());

        mockMvc.perform(get("/api/admin/categories").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data[?(@.slug == 'temporary-shelf')]").isNotEmpty());
    }

    @Test
    void renamingToItsOwnSlugIsAllowed() throws Exception {
        String token = tokenFor("cat.admin5@nexbid.com", RoleName.ADMIN);
        String id = createCategory(token, "{\"name\":\"Garden Tools\"}");

        // EN: Same slug, new description — this must not read as a clash with itself.
        // VI: Cùng slug, đổi mô tả — không được coi đây là trùng với chính nó.
        mockMvc.perform(put("/api/admin/categories/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Garden Tools\",\"description\":\"Spades and shears.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Spades and shears."));
    }

    @Test
    void aBlankNameIsRejected() throws Exception {
        String token = tokenFor("cat.admin6@nexbid.com", RoleName.ADMIN);

        mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
