package com.nexbid.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Editing and removing products (guide §13). The rule that matters is that a lot under the hammer
 *     stops being the seller's to change.
 * VI: Sửa và xoá sản phẩm (guide §13). Luật quan trọng là lô đang đấu giá thì không còn thuộc quyền sửa
 *     của người bán nữa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class SellerProductManagementTest {

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Manage Test","email":"%s","password":"supersecret"}
                        """.formatted(email)));

        users.grantRole(email, RoleName.SELLER);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    private String categoryId() throws Exception {
        String body = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get(0).get("id").asString();
    }

    private String productFor(String token, String name) throws Exception {
        String body = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Original text.","categoryId":"%s",
                                 "condition":"GOOD","publishNow":true}
                                """.formatted(name, categoryId())))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    private String editBody(String name) throws Exception {
        return """
                {"name":"%s","description":"Rewritten.","categoryId":"%s","condition":"FAIR"}
                """.formatted(name, categoryId());
    }

    @Test
    void aSellerEditsTheirOwnProduct() throws Exception {
        String token = tokenFor("mng.edit@nexbid.com");
        String product = productFor(token, "Before");

        mockMvc.perform(put("/api/seller/products/" + product)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editBody("After")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("After"))
                .andExpect(jsonPath("$.data.description").value("Rewritten."))
                .andExpect(jsonPath("$.data.condition").value("FAIR"));
    }

    @Test
    void editingCanMoveItBackToDraft() throws Exception {
        String token = tokenFor("mng.draft@nexbid.com");
        String product = productFor(token, "Published");

        mockMvc.perform(put("/api/seller/products/" + product)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Published","description":"...","categoryId":"%s",
                                 "condition":"GOOD","published":false}
                                """.formatted(categoryId())))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void oneSellerCannotEditAnothersProduct() throws Exception {
        String owner = tokenFor("mng.owner@nexbid.com");
        String stranger = tokenFor("mng.stranger@nexbid.com");
        String product = productFor(owner, "Not yours");

        mockMvc.perform(put("/api/seller/products/" + product)
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editBody("Stolen")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void aProductInAnAuctionCannotBeEdited() throws Exception {
        String token = tokenFor("mng.inauction@nexbid.com");
        String product = productFor(token, "Under the hammer");

        // EN: Auctions arrive at function 12; until then the status is set directly to prove the rule.
        // VI: Đấu giá tới ở chức năng 12; tạm thời đặt thẳng trạng thái để chứng minh luật này.
        jdbc.update("UPDATE products SET status = 'IN_AUCTION' WHERE id = ?::uuid", product);

        mockMvc.perform(put("/api/seller/products/" + product)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(editBody("Changed mid-auction")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_EDITABLE"));
    }

    @Test
    void aProductInAnAuctionCannotBeDeleted() throws Exception {
        String token = tokenFor("mng.nodelete@nexbid.com");
        String product = productFor(token, "Locked");

        jdbc.update("UPDATE products SET status = 'IN_AUCTION' WHERE id = ?::uuid", product);

        // EN: This is the rule guide §13 states outright.
        // VI: Đây chính là luật guide §13 nêu thẳng ra.
        mockMvc.perform(delete("/api/seller/products/" + product)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_EDITABLE"));
    }

    @Test
    void aSoldProductIsKeptAsARecord() throws Exception {
        String token = tokenFor("mng.sold@nexbid.com");
        String product = productFor(token, "Already gone");

        jdbc.update("UPDATE products SET status = 'SOLD' WHERE id = ?::uuid", product);

        mockMvc.perform(delete("/api/seller/products/" + product)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_EDITABLE"));
    }

    @Test
    void deletingRemovesTheProductAndItsImageRows() throws Exception {
        String token = tokenFor("mng.delete@nexbid.com");
        String product = productFor(token, "Going away");

        mockMvc.perform(multipart("/api/seller/products/" + product + "/images")
                .file(new MockMultipartFile("files", "a.png", "image/png", PNG))
                .header("Authorization", "Bearer " + token));

        Integer before = jdbc.queryForObject(
                "SELECT count(*) FROM product_images WHERE product_id = ?::uuid", Integer.class, product);
        assertThat(before).isEqualTo(1);

        mockMvc.perform(delete("/api/seller/products/" + product)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // EN: The image rows go with it through the database cascade.
        // VI: Các dòng ảnh đi theo nhờ cascade của database.
        Integer after = jdbc.queryForObject(
                "SELECT count(*) FROM product_images WHERE product_id = ?::uuid", Integer.class, product);
        assertThat(after).isZero();

        mockMvc.perform(get("/api/seller/products/" + product)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void oneSellerCannotDeleteAnothersProduct() throws Exception {
        String owner = tokenFor("mng.owner2@nexbid.com");
        String stranger = tokenFor("mng.stranger2@nexbid.com");
        String product = productFor(owner, "Safe");

        mockMvc.perform(delete("/api/seller/products/" + product)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        // EN: And it is still there afterwards.
        // VI: Và sau đó nó vẫn còn nguyên.
        mockMvc.perform(get("/api/seller/products/" + product)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
    }

    @Test
    void editingWithAnUnknownCategoryIsRefused() throws Exception {
        String token = tokenFor("mng.badcat@nexbid.com");
        String product = productFor(token, "Fine for now");

        mockMvc.perform(put("/api/seller/products/" + product)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Fine","description":"...","categoryId":"%s","condition":"NEW"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }
}
