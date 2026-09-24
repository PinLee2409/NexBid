package com.nexbid.auction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Approving and rejecting (guide §17, spec §7.5). The completion criterion is that an auction only
 *     becomes public once an admin has let it through.
 * VI: Duyệt và từ chối (guide §17, spec §7.5). Tiêu chí hoàn thành là một phiên chỉ công khai sau khi
 *     admin cho đi tiếp.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class AuctionApprovalApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private record Lot(String auctionId, String productId) {
    }

    private String tokenFor(String email, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Approval Test","email":"%s","password":"supersecret"}
                        """.formatted(email)));

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

    /** EN: A lot already sent for approval. / VI: Một lô đã gửi duyệt sẵn. */
    private Lot submittedLot(String seller, String name, Instant start, Instant end) throws Exception {
        String categories = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();

        String productBody = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"...","categoryId":"%s",
                                 "condition":"GOOD","publishNow":true}
                                """.formatted(name, categoryId)))
                .andReturn().getResponse().getContentAsString();

        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();

        String auctionBody = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":1000,"minimumIncrement":50,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId, start, end)))
                .andReturn().getResponse().getContentAsString();

        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();

        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit")
                .header("Authorization", "Bearer " + seller));

        return new Lot(auctionId, productId);
    }

    @Test
    void approvingAFutureStartGivesScheduled() throws Exception {
        String seller = tokenFor("app.future@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("app.admin1@nexbid.com", RoleName.ADMIN);

        Lot lot = submittedLot(seller, "Opens later",
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(2)));

        mockMvc.perform(post("/api/admin/auctions/" + lot.auctionId() + "/approve")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    @Test
    void approvingAStartThatHasPassedOpensBiddingImmediately() throws Exception {
        String seller = tokenFor("app.now@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("app.admin2@nexbid.com", RoleName.ADMIN);

        Lot lot = submittedLot(seller, "Should open now",
                Instant.now().plus(Duration.ofMinutes(1)),
                Instant.now().plus(Duration.ofHours(2)));

        // EN: The seller scheduled it for a minute out; by the time the admin gets to it, that has passed.
        // VI: Người bán hẹn sau một phút; tới lúc admin xử lý thì mốc đó đã trôi qua.
        jdbc.update("UPDATE auctions SET start_time = now() - interval '5 minutes' WHERE id = ?::uuid",
                lot.auctionId());

        mockMvc.perform(post("/api/admin/auctions/" + lot.auctionId() + "/approve")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void approvingLocksTheProductToTheAuction() throws Exception {
        String seller = tokenFor("app.lock@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("app.admin3@nexbid.com", RoleName.ADMIN);

        Lot lot = submittedLot(seller, "About to be locked",
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(2)));

        mockMvc.perform(post("/api/admin/auctions/" + lot.auctionId() + "/approve")
                .header("Authorization", "Bearer " + admin));

        mockMvc.perform(get("/api/seller/products/" + lot.productId())
                        .header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.data.status").value("IN_AUCTION"));

        // EN: And the rule written at function 13 now bites for real.
        // VI: Và luật viết ở chức năng 13 tới giờ mới thực sự có hiệu lực.
        mockMvc.perform(put("/api/seller/products/" + lot.productId())
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sneaky rename","description":"...","categoryId":"%s","condition":"NEW"}
                                """.formatted(objectMapper.readTree(
                                mockMvc.perform(get("/api/categories"))
                                        .andReturn().getResponse().getContentAsString())
                                .get("data").get(0).get("id").asString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_EDITABLE"));
    }

    @Test
    void rejectingRecordsTheReasonAndLeavesTheProductFree() throws Exception {
        String seller = tokenFor("app.reject@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("app.admin4@nexbid.com", RoleName.ADMIN);

        Lot lot = submittedLot(seller, "Not good enough",
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(2)));

        mockMvc.perform(post("/api/admin/auctions/" + lot.auctionId() + "/reject")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Invalid product information\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Invalid product information"));

        // EN: Still AVAILABLE, so the seller can fix the problem and try again.
        // VI: Vẫn AVAILABLE, nên người bán sửa chỗ sai rồi thử lại được.
        mockMvc.perform(get("/api/seller/products/" + lot.productId())
                        .header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    void aRejectedProductCanBeListedAgain() throws Exception {
        String seller = tokenFor("app.relist@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("app.admin5@nexbid.com", RoleName.ADMIN);

        Lot lot = submittedLot(seller, "Second chance",
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(2)));

        mockMvc.perform(post("/api/admin/auctions/" + lot.auctionId() + "/reject")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Photos are unclear\"}"));

        // EN: The partial unique index leaves out REJECTED, which is what makes this possible.
        // VI: Index duy nhất có điều kiện bỏ qua REJECTED — chính điều đó khiến việc này làm được.
        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":2000,"minimumIncrement":100,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(lot.productId(),
                                Instant.now().plus(Duration.ofHours(3)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectingWithoutAReasonIsRefused() throws Exception {
        String seller = tokenFor("app.noreason@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("app.admin6@nexbid.com", RoleName.ADMIN);

        Lot lot = submittedLot(seller, "Needs a reason",
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(2)));

        // EN: A seller told no deserves to know what to fix.
        // VI: Người bán bị từ chối xứng đáng biết phải sửa gì.
        mockMvc.perform(post("/api/admin/auctions/" + lot.auctionId() + "/reject")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void aDraftCannotBeApproved() throws Exception {
        String seller = tokenFor("app.draft@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("app.admin7@nexbid.com", RoleName.ADMIN);

        Lot lot = submittedLot(seller, "Back to draft",
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(2)));

        jdbc.update("UPDATE auctions SET status = 'DRAFT' WHERE id = ?::uuid", lot.auctionId());

        mockMvc.perform(post("/api/admin/auctions/" + lot.auctionId() + "/approve")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_PENDING"));
    }

    @Test
    void approvingTwiceIsRefused() throws Exception {
        String seller = tokenFor("app.twice@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("app.admin8@nexbid.com", RoleName.ADMIN);

        Lot lot = submittedLot(seller, "Only once",
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(2)));

        mockMvc.perform(post("/api/admin/auctions/" + lot.auctionId() + "/approve")
                .header("Authorization", "Bearer " + admin));

        // EN: Two reviewers with the queue open, the second arriving a moment later.
        // VI: Hai người duyệt cùng mở hàng chờ, người thứ hai tới sau một nhịp.
        mockMvc.perform(post("/api/admin/auctions/" + lot.auctionId() + "/approve")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_PENDING"));
    }

    @Test
    void aLotWhoseClockRanOutCannotBeApproved() throws Exception {
        String seller = tokenFor("app.expired@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("app.admin9@nexbid.com", RoleName.ADMIN);

        Lot lot = submittedLot(seller, "Sat in the queue too long",
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(2)));

        // EN: It waited so long that its end time went by. Approving it would create an auction that can
        //     never take a bid.
        // VI: Nó chờ lâu tới mức mốc kết thúc đã trôi qua. Duyệt nó sẽ tạo ra phiên không bao giờ nhận
        //     được lượt trả giá nào.
        jdbc.update("UPDATE auctions SET start_time = now() - interval '3 hours', "
                + "end_time = now() - interval '1 hour' WHERE id = ?::uuid", lot.auctionId());

        mockMvc.perform(post("/api/admin/auctions/" + lot.auctionId() + "/approve")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("AUCTION_SCHEDULE_INVALID"));
    }

    @Test
    void aSellerCannotApproveTheirOwnAuction() throws Exception {
        String seller = tokenFor("app.selfapprove@nexbid.com", RoleName.SELLER);

        Lot lot = submittedLot(seller, "Approving myself",
                Instant.now().plus(Duration.ofHours(1)),
                Instant.now().plus(Duration.ofHours(2)));

        mockMvc.perform(post("/api/admin/auctions/" + lot.auctionId() + "/approve")
                        .header("Authorization", "Bearer " + seller))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
