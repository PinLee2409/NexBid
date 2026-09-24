package com.nexbid.auction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Submitting for approval (guide §15) and the admin's review queue (guide §16).
 * VI: Gửi duyệt (guide §15) và hàng chờ duyệt của admin (guide §16).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class AuctionReviewApiTest {

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
    private ObjectMapper objectMapper;

    private String tokenFor(String email, String name, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","email":"%s","password":"supersecret"}
                        """.formatted(name, email)));

        if (role != null) {
            users.grantRole(email, role);
        }

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    /** EN: A draft auction with a photo on its product. / VI: Một phiên nháp, sản phẩm có sẵn một tấm ảnh. */
    private String draftAuction(String token, String productName) throws Exception {
        String categories = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();

        String productBody = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"Full description.","categoryId":"%s",
                                 "condition":"LIKE_NEW","publishNow":true}
                                """.formatted(productName, categoryId)))
                .andReturn().getResponse().getContentAsString();

        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();

        mockMvc.perform(multipart("/api/seller/products/" + productId + "/images")
                .file(new MockMultipartFile("files", "cover.png", "image/png", PNG))
                .header("Authorization", "Bearer " + token));

        Instant start = Instant.now().plus(Duration.ofHours(1));
        Instant end = Instant.now().plus(Duration.ofHours(2));

        String auctionBody = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":1000,"minimumIncrement":50,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId, start, end)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(auctionBody).get("data").get("id").asString();
    }

    @Test
    void submittingMovesADraftToPendingApproval() throws Exception {
        String seller = tokenFor("rev.submit@nexbid.com", "Submitting Seller", RoleName.SELLER);
        String auction = draftAuction(seller, "Ready to review");

        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit")
                        .header("Authorization", "Bearer " + seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
    }

    @Test
    void submittingTwiceIsRefused() throws Exception {
        String seller = tokenFor("rev.twice@nexbid.com", "Twice Seller", RoleName.SELLER);
        String auction = draftAuction(seller, "Sent once");

        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit")
                .header("Authorization", "Bearer " + seller));

        // EN: Guide §15 — only a draft can be submitted.
        // VI: Guide §15 — chỉ bản nháp mới gửi được.
        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit")
                        .header("Authorization", "Bearer " + seller))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_EDITABLE"));
    }

    @Test
    void afterSubmittingTheSellerCanNoLongerEditIt() throws Exception {
        String seller = tokenFor("rev.locked@nexbid.com", "Locked Seller", RoleName.SELLER);
        String auction = draftAuction(seller, "Locked after submit");

        String detail = mockMvc.perform(get("/api/seller/auctions/" + auction)
                        .header("Authorization", "Bearer " + seller))
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(detail).get("data").get("productId").asString();

        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit")
                .header("Authorization", "Bearer " + seller));

        mockMvc.perform(put("/api/seller/auctions/" + auction)
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":1,"minimumIncrement":1,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId,
                                Instant.now().plus(Duration.ofHours(1)),
                                Instant.now().plus(Duration.ofHours(2)))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_EDITABLE"));
    }

    @Test
    void oneSellerCannotSubmitAnothersAuction() throws Exception {
        String owner = tokenFor("rev.owner@nexbid.com", "Owner", RoleName.SELLER);
        String stranger = tokenFor("rev.stranger@nexbid.com", "Stranger", RoleName.SELLER);
        String auction = draftAuction(owner, "Not yours to send");

        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }

    @Test
    void submittedAuctionsAppearInTheAdminQueue() throws Exception {
        String seller = tokenFor("rev.queue@nexbid.com", "Queue Seller", RoleName.SELLER);
        String admin = tokenFor("rev.admin@nexbid.com", "Reviewer", RoleName.ADMIN);
        String auction = draftAuction(seller, "Waiting in line");

        // EN: Not there while it is still a draft.
        // VI: Còn là nháp thì chưa có mặt ở đó.
        mockMvc.perform(get("/api/admin/auctions/pending").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.data[?(@.id == '" + auction + "')]").isEmpty());

        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit")
                .header("Authorization", "Bearer " + seller));

        mockMvc.perform(get("/api/admin/auctions/pending").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == '" + auction + "')]").isNotEmpty());
    }

    @Test
    void theReviewScreenGetsEverythingItNeedsInOneResponse() throws Exception {
        String seller = tokenFor("rev.detail@nexbid.com", "Detailed Seller", RoleName.SELLER);
        String admin = tokenFor("rev.admin2@nexbid.com", "Reviewer Two", RoleName.ADMIN);
        String auction = draftAuction(seller, "Fully described");

        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit")
                .header("Authorization", "Bearer " + seller));

        // EN: Guide §16 lists exactly these: product, seller, price, times, images, description.
        // VI: Guide §16 liệt kê đúng những thứ này: sản phẩm, người bán, giá, mốc thời gian, ảnh, mô tả.
        mockMvc.perform(get("/api/admin/auctions/" + auction).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.auction.startingPrice").value(1000))
                .andExpect(jsonPath("$.data.auction.startTime").isNotEmpty())
                .andExpect(jsonPath("$.data.auction.endTime").isNotEmpty())
                .andExpect(jsonPath("$.data.product.name").value("Fully described"))
                .andExpect(jsonPath("$.data.product.description").value("Full description."))
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andExpect(jsonPath("$.data.seller.fullName").value("Detailed Seller"));
    }

    @Test
    void aSellerCannotReadTheAdminQueue() throws Exception {
        String seller = tokenFor("rev.nosy@nexbid.com", "Nosy Seller", RoleName.SELLER);

        mockMvc.perform(get("/api/admin/auctions/pending").header("Authorization", "Bearer " + seller))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void anAdminReadingAnUnknownAuctionGets404() throws Exception {
        String admin = tokenFor("rev.admin3@nexbid.com", "Reviewer Three", RoleName.ADMIN);

        mockMvc.perform(get("/api/admin/auctions/" + java.util.UUID.randomUUID())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }
}
