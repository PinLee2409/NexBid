package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Many lots from one CSV file with their photos; bad rows are reported, good ones become drafts.
 * VI: Nhiều lô từ một file CSV kèm ảnh; dòng lỗi được báo, dòng đúng thành bản nháp.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class LotImportApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Insight User","email":"%s","password":"supersecret"}
                """.formatted(email)));
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"supersecret"}
                """.formatted(email))).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    /** EN: An approved, open lot in the given category. / VI: Một lô đã duyệt, đang mở, trong danh mục cho trước. */
    /** EN: A category of the test's own; returns its slug. / VI: Một danh mục riêng của test; trả về slug. */
    private String categorySlug(String admin, String name) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("slug").asString();
    }

    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Test
    void eachRowBecomesADraftLotOrSaysWhyNot() throws Exception {
        String seller = tokenFor("imp.seller@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("imp.admin@nexbid.com", RoleName.ADMIN);
        String slug = categorySlug(admin, "Import cameras");
        String csv = "﻿name;description;category;condition;starting_price;increment;buy_now_price;start;end;images;"
                + "weight_g;length_cm;width_cm;height_cm\r\n"
                + "Máy ảnh Leica M6;\"Thân máy đẹp; kèm hộp,\nđủ phụ kiện\";" + slug + ";like new;1.500.000;100000;;"
                + "2030-01-10 09:00;2030-01-12 21:00;leica.png|LEICA-2.PNG;800;20;15;10\r\n"
                + "Bad lot;A long description of the item.;no-such;NEW;abc;100000;;2030-01-10 09:00;"
                + "2030-01-09 09:00;missing.jpg;;;;\r\n"
                + ";;;;;;;;;;;;;\r\n";
        var file = new org.springframework.mock.web.MockMultipartFile("file", "lots.csv", "text/csv",
                csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var first = new org.springframework.mock.web.MockMultipartFile("images", "leica.png", "image/png", PNG);
        var second = new org.springframework.mock.web.MockMultipartFile("images", "leica-2.png", "image/png", PNG);

        String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/seller/auctions/import").file(file).file(first).file(second)
                        .header("Authorization", "Bearer " + seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(1))
                .andExpect(jsonPath("$.data.failed").value(1))
                .andExpect(jsonPath("$.data.rows[0].line").value(2))
                .andExpect(jsonPath("$.data.rows[0].name").value("Máy ảnh Leica M6"))
                .andExpect(jsonPath("$.data.rows[1].errors", org.hamcrest.Matchers.hasItems(
                        org.hamcrest.Matchers.startsWith("category:"),
                        org.hamcrest.Matchers.startsWith("starting_price:"),
                        org.hamcrest.Matchers.startsWith("images: \"missing.jpg\""))))
                .andReturn().getResponse().getContentAsString();
        String auctionId = objectMapper.readTree(body).get("data").get("rows").get(0).get("auctionId").asString();

        mockMvc.perform(get("/api/seller/auctions/" + auctionId).header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.startingPrice").value(1500000))
                .andExpect(jsonPath("$.data.startTime").value("2030-01-10T02:00:00Z"));
        String productId = jdbc.queryForObject("SELECT product_id::text FROM auctions WHERE id = ?::uuid", String.class,
                auctionId);
        assertThat(jdbc.queryForObject("SELECT description FROM products WHERE id = ?::uuid", String.class, productId))
                .isEqualTo("Thân máy đẹp; kèm hộp,\nđủ phụ kiện");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE product_id = ?::uuid", Integer.class,
                productId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT weight_grams FROM products WHERE id = ?::uuid", Integer.class, productId))
                .isEqualTo(800);

        var empty = new org.springframework.mock.web.MockMultipartFile("file", "lots.csv", "text/csv",
                "name,description\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .multipart("/api/seller/auctions/import").file(empty).header("Authorization", "Bearer " + seller))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_INVALID"));
    }
}
