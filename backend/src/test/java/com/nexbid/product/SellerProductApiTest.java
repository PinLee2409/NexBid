package com.nexbid.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
 * EN: Seller products (guide §11). The criterion is "the product belongs to the right seller" — most of
 *     these tests are about exactly that.
 * VI: Sản phẩm của người bán (guide §11). Tiêu chí là "sản phẩm thuộc đúng người bán" — phần lớn test ở đây
 *     xoay quanh đúng điều đó.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class SellerProductApiTest {

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
                        {"fullName":"Product Test","email":"%s","password":"supersecret"}
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

    private String anyCategoryId() throws Exception {
        String body = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get(0).get("id").asString();
    }

    private String createProduct(String token, String name, boolean publishNow) throws Exception {
        String body = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"A description.","categoryId":"%s",
                                 "condition":"LIKE_NEW","publishNow":%s}
                                """.formatted(name, anyCategoryId(), publishNow)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    @Test
    void aSellerCreatesAProductAndItIsTheirs() throws Exception {
        String token = tokenFor("seller.create@nexbid.com", RoleName.SELLER);

        String meBody = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String myId = objectMapper.readTree(meBody).get("data").get("id").asString();

        mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"MacBook Pro M3","description":"Barely used.","categoryId":"%s",
                                 "condition":"LIKE_NEW"}
                                """.formatted(anyCategoryId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("MacBook Pro M3"))
                .andExpect(jsonPath("$.data.condition").value("LIKE_NEW"))
                // EN: This is the completion criterion of guide §11.
                // VI: Đây chính là tiêu chí hoàn thành của guide §11.
                .andExpect(jsonPath("$.data.sellerId").value(myId));
    }

    @Test
    void withoutPublishNowItStartsAsADraft() throws Exception {
        String token = tokenFor("seller.draft@nexbid.com", RoleName.SELLER);

        mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Half written","description":"...","categoryId":"%s","condition":"GOOD"}
                                """.formatted(anyCategoryId())))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void publishNowMakesItAvailable() throws Exception {
        String token = tokenFor("seller.publish@nexbid.com", RoleName.SELLER);

        mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ready to sell","description":"...","categoryId":"%s",
                                 "condition":"NEW","publishNow":true}
                                """.formatted(anyCategoryId())))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    void aBuyerCannotCreateProducts() throws Exception {
        String token = tokenFor("buyer.product@nexbid.com", null);

        mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nope","description":"...","categoryId":"%s","condition":"NEW"}
                                """.formatted(anyCategoryId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void oneSellerNeverSeesAnothersProducts() throws Exception {
        String alice = tokenFor("alice.seller@nexbid.com", RoleName.SELLER);
        String bob = tokenFor("bob.seller@nexbid.com", RoleName.SELLER);

        createProduct(alice, "Alice Item", true);
        createProduct(bob, "Bob Item", true);

        String aliceList = mockMvc.perform(get("/api/seller/products")
                        .header("Authorization", "Bearer " + alice))
                .andReturn().getResponse().getContentAsString();

        assertThat(aliceList).contains("Alice Item").doesNotContain("Bob Item");
    }

    @Test
    void readingAnotherSellersProductAnswersLikeItDoesNotExist() throws Exception {
        String alice = tokenFor("alice2.seller@nexbid.com", RoleName.SELLER);
        String bob = tokenFor("bob2.seller@nexbid.com", RoleName.SELLER);

        String bobsProduct = createProduct(bob, "Bob Private", true);

        // EN: 404, not 403 — a different answer would confirm that this id is real.
        // VI: 404 chứ không phải 403 — trả lời khác đi là đã xác nhận id này có thật.
        mockMvc.perform(get("/api/seller/products/" + bobsProduct)
                        .header("Authorization", "Bearer " + alice))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void anUnknownCategoryIsRefused() throws Exception {
        String token = tokenFor("seller.badcat@nexbid.com", RoleName.SELLER);

        mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Orphan","description":"...","categoryId":"%s","condition":"NEW"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void missingFieldsAreAllReportedAtOnce() throws Exception {
        String token = tokenFor("seller.invalid@nexbid.com", RoleName.SELLER);

        mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.description").exists())
                .andExpect(jsonPath("$.details.categoryId").exists())
                .andExpect(jsonPath("$.details.condition").exists());
    }

    @Test
    void anUnknownConditionIsAReadableErrorNotACrash() throws Exception {
        String token = tokenFor("seller.badcond@nexbid.com", RoleName.SELLER);

        mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Odd","description":"...","categoryId":"%s","condition":"PRISTINE"}
                                """.formatted(anyCategoryId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void withoutATokenNothingIsReachable() throws Exception {
        mockMvc.perform(get("/api/seller/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
    }
}
