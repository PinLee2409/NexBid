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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Product images (guide §12). The rule the guide states is ownership; the rest of these tests are about
 *     not trusting what an upload claims to be.
 * VI: Ảnh sản phẩm (guide §12). Luật guide nêu ra là quyền sở hữu; phần còn lại của các test là về việc
 *     không tin vào lời khai của file tải lên.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class ProductImageApiTest {

    /** EN: A real 1x1 PNG, so the magic-byte check passes. / VI: Một file PNG 1x1 thật, để qua được bước kiểm byte. */
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
            0x42, 0x60, (byte) 0x82
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Image Test","email":"%s","password":"supersecret"}
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

    private String productFor(String token) throws Exception {
        String categories = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();

        String body = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"With photos","description":"...","categoryId":"%s","condition":"GOOD"}
                                """.formatted(categoryId)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("files", name, "image/png", PNG);
    }

    @Test
    void uploadingGivesTheFirstImageTheCoverPosition() throws Exception {
        String token = tokenFor("img.upload@nexbid.com");
        String product = productFor(token);

        mockMvc.perform(multipart("/api/seller/products/" + product + "/images")
                        .file(png("front.png"))
                        .file(png("back.png"))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[1].sortOrder").value(1))
                // EN: The stored name is generated, so the uploaded one never reaches the URL.
                // VI: Tên lưu trữ do server sinh, nên tên file tải lên không bao giờ lọt ra URL.
                .andExpect(jsonPath("$.data[0].url").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("front"))));
    }

    @Test
    void anUploadedImageCanBeFetchedWithoutAToken() throws Exception {
        String token = tokenFor("img.serve@nexbid.com");
        String product = productFor(token);

        String uploaded = mockMvc.perform(multipart("/api/seller/products/" + product + "/images")
                        .file(png("served.png"))
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        String url = objectMapper.readTree(uploaded).get("data").get(0).get("url").asString();

        // EN: A browser loading <img> sends no Authorization header. If this needs one, every photo breaks.
        // VI: Trình duyệt nạp thẻ <img> không gửi header Authorization. Nếu đường này cần token thì mọi ảnh đều vỡ.
        mockMvc.perform(get(url))
                .andExpect(status().isOk());
    }

    @Test
    void aTextFileRenamedToPngIsRefused() throws Exception {
        String token = tokenFor("img.fake@nexbid.com");
        String product = productFor(token);

        // EN: Right name, right Content-Type, wrong bytes. Only the bytes are believed.
        // VI: Đúng tên, đúng Content-Type, sai nội dung. Chỉ nội dung mới được tin.
        MockMultipartFile disguised = new MockMultipartFile(
                "files", "evil.png", "image/png", "#!/bin/sh\nrm -rf /".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/seller/products/" + product + "/images")
                        .file(disguised)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID"));
    }

    @Test
    void anotherSellerCannotAddImagesToYourProduct() throws Exception {
        String owner = tokenFor("img.owner@nexbid.com");
        String stranger = tokenFor("img.stranger@nexbid.com");
        String product = productFor(owner);

        // EN: 404, not 403 — the same answer a product that does not exist would give.
        // VI: 404 chứ không phải 403 — cùng câu trả lời với một sản phẩm không tồn tại.
        mockMvc.perform(multipart("/api/seller/products/" + product + "/images")
                        .file(png("sneak.png"))
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void choosingACoverMovesItToTheFront() throws Exception {
        String token = tokenFor("img.cover@nexbid.com");
        String product = productFor(token);

        String uploaded = mockMvc.perform(multipart("/api/seller/products/" + product + "/images")
                        .file(png("a.png"))
                        .file(png("b.png"))
                        .file(png("c.png"))
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        String third = objectMapper.readTree(uploaded).get("data").get(2).get("id").asString();

        mockMvc.perform(put("/api/seller/products/" + product + "/images/" + third + "/cover")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(third))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[1].sortOrder").value(1))
                .andExpect(jsonPath("$.data[2].sortOrder").value(2));
    }

    @Test
    void deletingClosesTheGapInTheOrder() throws Exception {
        String token = tokenFor("img.delete@nexbid.com");
        String product = productFor(token);

        String uploaded = mockMvc.perform(multipart("/api/seller/products/" + product + "/images")
                        .file(png("a.png"))
                        .file(png("b.png"))
                        .file(png("c.png"))
                        .header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();

        String middle = objectMapper.readTree(uploaded).get("data").get(1).get("id").asString();

        // EN: Positions must stay 0,1 — a gap would eventually break the unique index.
        // VI: Vị trí phải còn 0,1 — để hở một chỗ thì sớm muộn cũng vi phạm ràng buộc duy nhất.
        mockMvc.perform(delete("/api/seller/products/" + product + "/images/" + middle)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].sortOrder").value(0))
                .andExpect(jsonPath("$.data[1].sortOrder").value(1));
    }

    @Test
    void deletingAnImageThatIsNotYoursIs404() throws Exception {
        String owner = tokenFor("img.owner2@nexbid.com");
        String stranger = tokenFor("img.stranger2@nexbid.com");
        String product = productFor(owner);

        String uploaded = mockMvc.perform(multipart("/api/seller/products/" + product + "/images")
                        .file(png("a.png"))
                        .header("Authorization", "Bearer " + owner))
                .andReturn().getResponse().getContentAsString();

        String imageId = objectMapper.readTree(uploaded).get("data").get(0).get("id").asString();

        mockMvc.perform(delete("/api/seller/products/" + product + "/images/" + imageId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOversizedUploadIs413NotAServerFault() throws Exception {
        String token = tokenFor("img.big@nexbid.com");
        String product = productFor(token);

        // EN: A real PNG header padded past the limit. The servlet rejects it before any controller runs,
        //     which is exactly the path that used to answer 500.
        // VI: Header PNG thật nhồi vượt quá giới hạn. Servlet chặn trước khi controller chạy — đúng con đường
        //     trước đây trả về 500.
        byte[] oversized = new byte[6 * 1024 * 1024];
        System.arraycopy(PNG, 0, oversized, 0, PNG.length);

        mockMvc.perform(multipart("/api/seller/products/" + product + "/images")
                        .file(new MockMultipartFile("files", "huge.png", "image/png", oversized))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"));
    }

    @Test
    void aJpegIsAcceptedToo() throws Exception {
        String token = tokenFor("img.jpeg@nexbid.com");
        String product = productFor(token);

        // EN: Only PNG was covered before. JPEG has a different signature, so it needs its own proof.
        // VI: Trước đây mới chỉ phủ PNG. JPEG có chữ ký khác nên cần bằng chứng riêng.
        byte[] jpeg = new byte[64];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        jpeg[3] = (byte) 0xE0;

        mockMvc.perform(multipart("/api/seller/products/" + product + "/images")
                        .file(new MockMultipartFile("files", "photo.jpg", "image/jpeg", jpeg))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data[0].url").value(org.hamcrest.Matchers.endsWith(".jpg")));
    }

    @Test
    void anEmptyUploadIsRefused() throws Exception {
        String token = tokenFor("img.empty@nexbid.com");
        String product = productFor(token);

        mockMvc.perform(multipart("/api/seller/products/" + product + "/images")
                        .file(new MockMultipartFile("files", "nothing.png", "image/png", new byte[0]))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IMAGE_INVALID"));
    }

    @Test
    void aProductCannotHoldMoreThanTenImages() throws Exception {
        String token = tokenFor("img.limit@nexbid.com");
        String product = productFor(token);

        var request = multipart("/api/seller/products/" + product + "/images")
                .header("Authorization", "Bearer " + token);
        for (int i = 0; i < 11; i++) {
            request = request.file(png("photo" + i + ".png"));
        }

        mockMvc.perform(request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IMAGE_LIMIT_REACHED"));
    }
}
