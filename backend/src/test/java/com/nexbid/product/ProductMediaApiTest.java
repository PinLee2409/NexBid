package com.nexbid.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: A product's short video (recognised by its bytes, not its name) and its 360° flag.
 * VI: Video ngắn của sản phẩm (nhận dạng theo byte, không theo tên) và cờ xem 360°.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class ProductMediaApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private ObjectMapper objectMapper;

    private String sellerToken(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Media Seller","email":"%s","password":"supersecret"}""".formatted(email)));
        users.grantRole(email, RoleName.SELLER);
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"supersecret\"}"))
                .andReturn().getResponse().getContentAsString()).get("data").get("accessToken").asString();
    }

    private static MockMultipartFile file(String name, String type, byte[] head) {
        byte[] bytes = new byte[2048];
        System.arraycopy(head, 0, bytes, 0, head.length);
        return new MockMultipartFile("file", name, type, bytes);
    }

    @Test
    void aProductTakesOneVideoAndCanBeA360View() throws Exception {
        String seller = sellerToken("media.seller@nexbid.com");
        String other = sellerToken("media.other@nexbid.com");
        String categoryId = objectMapper.readTree(mockMvc.perform(get("/api/categories")).andReturn().getResponse()
                .getContentAsString()).get("data").get(0).get("id").asString();
        String productId = objectMapper.readTree(mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + seller).contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Media camera","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"GOOD","publishNow":true}""".formatted(categoryId)))
                .andReturn().getResponse().getContentAsString()).get("data").get("id").asString();
        String video = "/api/seller/products/" + productId + "/video";

        byte[] mp4 = "\0\0\0\u0018ftypisom".getBytes(StandardCharsets.ISO_8859_1);
        byte[] avif = "\0\0\0\u0018ftypavif".getBytes(StandardCharsets.ISO_8859_1);
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        mockMvc.perform(multipart(HttpMethod.PUT, video).file(file("clip.mp4", "video/mp4", png))
                        .header("Authorization", "Bearer " + seller))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VIDEO_INVALID"));
        mockMvc.perform(multipart(HttpMethod.PUT, video).file(file("clip.mp4", "video/mp4", avif))
                        .header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.code").value("VIDEO_INVALID"));
        mockMvc.perform(multipart(HttpMethod.PUT, video).file(file("clip.mp4", "video/mp4", mp4))
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart(HttpMethod.PUT, video).file(file("../../evil.webm", "video/webm",
                        new byte[] {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3}))
                        .header("Authorization", "Bearer " + seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.videoUrl").value(org.hamcrest.Matchers.matchesPattern(
                        "/media/products/[0-9a-f-]{36}\\.webm")));
        mockMvc.perform(multipart(HttpMethod.PUT, video).file(file("clip.mp4", "video/mp4", mp4))
                        .header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.data.videoUrl").value(org.hamcrest.Matchers.endsWith(".mp4")));

        mockMvc.perform(put("/api/seller/products/" + productId + "/spin").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(jsonPath("$.data.spinView").value(true));
        mockMvc.perform(delete(video).header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.data.videoUrl").value(org.hamcrest.Matchers.nullValue()));
    }
}
