package com.nexbid.auction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The public list (guide §18, spec §7.6). The rule that matters most is what does <em>not</em> appear.
 * VI: Danh sách công khai (guide §18, spec §7.6). Luật quan trọng nhất là những gì <em>không</em> xuất hiện.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class PublicAuctionListApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private String seller;
    private String admin;

    @BeforeEach
    void cleanSlate() throws Exception {
        // EN: The list is global, so leftovers from other tests would make the assertions meaningless.
        // VI: Danh sách này là toàn cục, nên dữ liệu thừa từ test khác sẽ làm các khẳng định vô nghĩa.
        jdbc.update("DELETE FROM auctions");

        seller = tokenFor("list.seller@nexbid.com", RoleName.SELLER);
        admin = tokenFor("list.admin@nexbid.com", RoleName.ADMIN);
    }

    private String tokenFor(String email, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Browse Test","email":"%s","password":"supersecret"}
                        """.formatted(email)));

        users.grantRole(email, role);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    private String categoryId(String slug) throws Exception {
        String body = mockMvc.perform(get("/api/categories/" + slug))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    /** EN: A lot that has been through approval, so it is public. / VI: Một lô đã qua duyệt nên đã công khai. */
    private String approvedLot(String name, String categorySlug, BigDecimal price, Duration endsIn)
            throws Exception {

        String productBody = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"...","categoryId":"%s",
                                 "condition":"GOOD","publishNow":true}
                                """.formatted(name, categoryId(categorySlug))))
                .andReturn().getResponse().getContentAsString();

        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();

        String auctionBody = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":%s,"minimumIncrement":10,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId, price.toPlainString(),
                                Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(endsIn))))
                .andReturn().getResponse().getContentAsString();

        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();

        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit")
                .header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve")
                .header("Authorization", "Bearer " + admin));

        return auctionId;
    }

    /** EN: Submitted but never approved. / VI: Đã gửi nhưng chưa được duyệt. */
    private String pendingLot(String name) throws Exception {
        String productBody = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"...","categoryId":"%s",
                                 "condition":"GOOD","publishNow":true}
                                """.formatted(name, categoryId("watches"))))
                .andReturn().getResponse().getContentAsString();

        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();

        String auctionBody = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":100,"minimumIncrement":10,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId,
                                Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();

        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();

        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit")
                .header("Authorization", "Bearer " + seller));

        return auctionId;
    }

    @Test
    void anyoneCanBrowseWithoutSigningIn() throws Exception {
        approvedLot("Open to all", "watches", new BigDecimal("500"), Duration.ofHours(4));

        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(12))
                .andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    void unapprovedAuctionsAreInvisible() throws Exception {
        String hidden = pendingLot("Still waiting");
        approvedLot("Visible", "art", new BigDecimal("100"), Duration.ofHours(4));

        // EN: The completion criterion of function 17 — nothing is public until an admin lets it through.
        // VI: Tiêu chí hoàn thành của chức năng 17 — không gì công khai cho tới khi admin cho đi tiếp.
        mockMvc.perform(get("/api/auctions"))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[?(@.auction.id == '" + hidden + "')]").isEmpty());
    }

    @Test
    void aRejectedAuctionIsInvisibleToo() throws Exception {
        String rejected = pendingLot("Turned down");

        mockMvc.perform(post("/api/admin/auctions/" + rejected + "/reject")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Not suitable\"}"));

        mockMvc.perform(get("/api/auctions"))
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test
    void aCardCarriesEverythingItNeedsToRender() throws Exception {
        approvedLot("Full card", "cameras", new BigDecimal("750"), Duration.ofHours(4));

        // EN: Guide §18 lists image, name, current price, bid count, countdown and status.
        // VI: Guide §18 liệt kê ảnh, tên, giá hiện tại, số lượt, đồng hồ và trạng thái.
        mockMvc.perform(get("/api/auctions"))
                .andExpect(jsonPath("$.data.items[0].product.name").value("Full card"))
                .andExpect(jsonPath("$.data.items[0].auction.currentPrice").value(750))
                .andExpect(jsonPath("$.data.items[0].auction.bidCount").value(0))
                .andExpect(jsonPath("$.data.items[0].auction.endTime").isNotEmpty())
                .andExpect(jsonPath("$.data.items[0].auction.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.items[0].category.slug").value("cameras"))
                .andExpect(jsonPath("$.data.items[0].seller.displayName").value("Browse Test"));
    }

    @Test
    void filteringByCategoryNarrowsTheList() throws Exception {
        approvedLot("A watch", "watches", new BigDecimal("100"), Duration.ofHours(4));
        approvedLot("A camera", "cameras", new BigDecimal("200"), Duration.ofHours(4));

        mockMvc.perform(get("/api/auctions").param("category", "watches"))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].product.name").value("A watch"));
    }

    @Test
    void aCategoryWithNothingInItReturnsNothing() throws Exception {
        approvedLot("A watch", "watches", new BigDecimal("100"), Duration.ofHours(4));

        // EN: An empty match must return zero rows, not fall through to everything.
        // VI: Không khớp gì thì phải trả về 0 dòng, không được rơi xuống thành lấy tất cả.
        mockMvc.perform(get("/api/auctions").param("category", "sneakers"))
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test
    void filteringByPriceRange() throws Exception {
        approvedLot("Cheap", "art", new BigDecimal("50"), Duration.ofHours(4));
        approvedLot("Middle", "art", new BigDecimal("500"), Duration.ofHours(4));
        approvedLot("Dear", "art", new BigDecimal("5000"), Duration.ofHours(4));

        mockMvc.perform(get("/api/auctions").param("minPrice", "100").param("maxPrice", "1000"))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].product.name").value("Middle"));
    }

    @Test
    void sortingByPrice() throws Exception {
        approvedLot("Cheap", "art", new BigDecimal("50"), Duration.ofHours(4));
        approvedLot("Dear", "art", new BigDecimal("5000"), Duration.ofHours(4));

        mockMvc.perform(get("/api/auctions").param("sort", "PRICE_ASC"))
                .andExpect(jsonPath("$.data.items[0].product.name").value("Cheap"));

        mockMvc.perform(get("/api/auctions").param("sort", "PRICE_DESC"))
                .andExpect(jsonPath("$.data.items[0].product.name").value("Dear"));
    }

    @Test
    void sortingByEndingSoon() throws Exception {
        approvedLot("Later", "art", new BigDecimal("100"), Duration.ofHours(8));
        approvedLot("Sooner", "art", new BigDecimal("100"), Duration.ofHours(2));

        mockMvc.perform(get("/api/auctions").param("sort", "ENDING_SOON"))
                .andExpect(jsonPath("$.data.items[0].product.name").value("Sooner"));
    }

    @Test
    void endingSoonOnlyCountsLotsThatAreActuallyOpen() throws Exception {
        // EN: Created with a valid schedule, then moved into the closing window — the helper always puts
        //     the start half an hour out, and an end before that would be refused.
        // VI: Tạo với lịch hợp lệ rồi mới đẩy vào khoảng sắp đóng — helper luôn đặt giờ mở sau nửa tiếng,
        //     và mốc kết thúc trước đó sẽ bị từ chối.
        String soon = approvedLot("Closing", "art", new BigDecimal("100"), Duration.ofHours(4));
        approvedLot("Not yet", "art", new BigDecimal("100"), Duration.ofHours(8));

        // EN: Both are SCHEDULED until their start times pass; "ending soon" means open and about to close.
        // VI: Cả hai còn SCHEDULED cho tới khi qua giờ mở; "sắp đóng" nghĩa là đang mở và sắp tới lúc đóng.
        mockMvc.perform(get("/api/auctions").param("endingSoon", "true"))
                .andExpect(jsonPath("$.data.totalItems").value(0));

        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 minute', "
                + "end_time = now() + interval '20 minutes' WHERE id = ?::uuid", soon);

        mockMvc.perform(get("/api/auctions").param("endingSoon", "true"))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].product.name").value("Closing"));
    }

    @Test
    void pagesAreOneBasedAndCapped() throws Exception {
        for (int i = 0; i < 3; i++) {
            approvedLot("Lot " + i, "art", new BigDecimal("100"), Duration.ofHours(4));
        }

        mockMvc.perform(get("/api/auctions").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(get("/api/auctions").param("size", "2").param("page", "2"))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.page").value(2));

        // EN: An unbounded size would let one request pull the whole table.
        // VI: Không chặn trên thì một request có thể kéo về cả bảng.
        mockMvc.perform(get("/api/auctions").param("size", "5000"))
                .andExpect(jsonPath("$.data.pageSize").value(Matchers.lessThanOrEqualTo(50)));
    }
}
