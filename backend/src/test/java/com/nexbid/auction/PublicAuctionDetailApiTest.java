package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
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
 * EN: The public lot page (guide §19, spec §7.7).
 * VI: Trang lô công khai (guide §19, spec §7.7).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class PublicAuctionDetailApiTest {

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

    private String tokenFor(String email, String name, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","email":"%s","password":"supersecret"}
                        """.formatted(name, email)));

        users.grantRole(email, role);

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersecret"}
                                """.formatted(email)))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    /** EN: A lot with a photo, submitted and approved. / VI: Một lô có ảnh, đã gửi và đã duyệt. */
    private String approvedLot(String seller, String admin, String name) throws Exception {
        String categories = mockMvc.perform(get("/api/categories"))
                .andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();

        String productBody = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(name, categoryId)))
                .andReturn().getResponse().getContentAsString();

        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();

        mockMvc.perform(multipart("/api/seller/products/" + productId + "/images")
                .file(new MockMultipartFile("files", "cover.png", "image/png", PNG))
                .header("Authorization", "Bearer " + seller));

        String auctionBody = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":32500000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId,
                                Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();

        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();

        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit")
                .header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve")
                .header("Authorization", "Bearer " + admin));

        return auctionId;
    }

    @Test
    void thePageCarriesEverythingTheSpecAsksFor() throws Exception {
        String seller = tokenFor("det.seller@nexbid.com", "Detail Seller", RoleName.SELLER);
        String admin = tokenFor("det.admin@nexbid.com", "Detail Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "MacBook Pro M3");

        // EN: Guide §19 lists twelve things; every one of them is asserted here.
        // VI: Guide §19 liệt kê mười hai thứ; mỗi thứ đều được khẳng định ở đây.
        mockMvc.perform(get("/api/auctions/" + auction))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product.name").value("MacBook Pro M3"))
                .andExpect(jsonPath("$.data.product.description").value("A long description of the item."))
                .andExpect(jsonPath("$.data.images.length()").value(1))
                .andExpect(jsonPath("$.data.seller.displayName").value("Detail Seller"))
                .andExpect(jsonPath("$.data.auction.startingPrice").value(32500000))
                .andExpect(jsonPath("$.data.auction.currentPrice").value(32500000))
                .andExpect(jsonPath("$.data.auction.minimumIncrement").value(500000))
                .andExpect(jsonPath("$.data.auction.bidCount").value(0))
                .andExpect(jsonPath("$.data.auction.startTime").isNotEmpty())
                .andExpect(jsonPath("$.data.auction.endTime").isNotEmpty())
                .andExpect(jsonPath("$.data.auction.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.minimumNextBid").value(32500000))
                .andExpect(jsonPath("$.data.serverTime").isNotEmpty());
    }

    @Test
    void noTokenIsNeeded() throws Exception {
        String seller = tokenFor("det.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("det.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "Open to everyone");

        mockMvc.perform(get("/api/auctions/" + auction))
                .andExpect(status().isOk());
    }

    @Test
    void serverTimeIsTheServersOwnClock() throws Exception {
        String seller = tokenFor("det.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String admin = tokenFor("det.admin3@nexbid.com", "Third Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "Timed lot");

        Instant before = Instant.now();
        String body = mockMvc.perform(get("/api/auctions/" + auction))
                .andReturn().getResponse().getContentAsString();
        Instant after = Instant.now();

        Instant serverTime = Instant.parse(
                objectMapper.readTree(body).get("data").get("serverTime").asString());

        // EN: Spec §11 — the browser renders the countdown but the server decides what time it is.
        // VI: Spec §11 — trình duyệt vẽ đồng hồ đếm ngược nhưng server mới quyết định bây giờ là mấy giờ.
        assertThat(serverTime).isBetween(before, after);
    }

    @Test
    void anUnapprovedLotIsNotReachableByItsDirectUrl() throws Exception {
        String seller = tokenFor("det.seller4@nexbid.com", "Fourth Seller", RoleName.SELLER);
        String admin = tokenFor("det.admin4@nexbid.com", "Fourth Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "Will be hidden");

        jdbc.update("UPDATE auctions SET status = 'PENDING_APPROVAL' WHERE id = ?::uuid", auction);

        // EN: The browse list hides it, so a direct link must hide it too — otherwise the list is theatre.
        // VI: Danh sách duyệt hàng đã giấu nó, nên vào thẳng đường dẫn cũng phải giấu — nếu không thì danh
        //     sách chỉ là màn kịch.
        mockMvc.perform(get("/api/auctions/" + auction))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }

    @Test
    void aRejectedLotIsNotReachableEither() throws Exception {
        String seller = tokenFor("det.seller5@nexbid.com", "Fifth Seller", RoleName.SELLER);
        String admin = tokenFor("det.admin5@nexbid.com", "Fifth Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "Refused later");

        jdbc.update("UPDATE auctions SET status = 'REJECTED' WHERE id = ?::uuid", auction);

        mockMvc.perform(get("/api/auctions/" + auction))
                .andExpect(status().isNotFound());
    }

    @Test
    void minimumNextBidRisesOnceThereAreBids() throws Exception {
        String seller = tokenFor("det.seller6@nexbid.com", "Sixth Seller", RoleName.SELLER);
        String admin = tokenFor("det.admin6@nexbid.com", "Sixth Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "Has bids");

        // EN: Bidding arrives at function 20; the counters are set directly to show the rule now.
        // VI: Việc trả giá tới ở chức năng 20; tạm đặt thẳng các con số để thấy luật này ngay.
        jdbc.update("UPDATE auctions SET bid_count = 3, current_price = 34000000 WHERE id = ?::uuid",
                auction);

        mockMvc.perform(get("/api/auctions/" + auction))
                .andExpect(jsonPath("$.data.auction.currentPrice").value(34000000))
                .andExpect(jsonPath("$.data.minimumNextBid").value(34500000));
    }

    @Test
    void aScheduledLotIsNotOpenForBiddingYet() throws Exception {
        String seller = tokenFor("det.seller7@nexbid.com", "Seventh Seller", RoleName.SELLER);
        String admin = tokenFor("det.admin7@nexbid.com", "Seventh Admin", RoleName.ADMIN);
        String auction = approvedLot(seller, admin, "Not open yet");

        mockMvc.perform(get("/api/auctions/" + auction))
                .andExpect(jsonPath("$.data.openForBidding").value(false));

        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 minute' "
                + "WHERE id = ?::uuid", auction);

        mockMvc.perform(get("/api/auctions/" + auction))
                .andExpect(jsonPath("$.data.openForBidding").value(true));
    }

    @Test
    void anUnknownIdIs404() throws Exception {
        mockMvc.perform(get("/api/auctions/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }
}
