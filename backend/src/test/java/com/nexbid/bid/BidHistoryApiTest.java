package com.nexbid.bid;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
 * EN: Bid history (guide §22). The list is public, so the interesting question is not what it shows but
 *     what it refuses to show.
 * VI: Lịch sử trả giá (guide §22). Danh sách là công khai, nên câu hỏi đáng quan tâm không phải nó hiện
 *     cái gì, mà nó từ chối hiện cái gì.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class BidHistoryApiTest {

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

    private String openLot(String seller, String admin, String name) throws Exception {
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

        String auctionBody = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
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

        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 minute' "
                + "WHERE id = ?::uuid", auctionId);

        return auctionId;
    }

    private void bid(String auctionId, String token, String amount) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    void theHistoryReadsNewestFirst() throws Exception {
        String seller = tokenFor("hist.seller1@nexbid.com", "History Seller", RoleName.SELLER);
        String admin = tokenFor("hist.admin1@nexbid.com", "History Admin", RoleName.ADMIN);
        String pin = tokenFor("hist.pin1@nexbid.com", "Pinnacle Buyer", RoleName.BUYER);
        String alex = tokenFor("hist.alex1@nexbid.com", "Alexander Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Busy lot");

        bid(auction, alex, "10000000");
        bid(auction, pin, "10500000");
        bid(auction, alex, "11000000");

        mockMvc.perform(get("/api/auctions/" + auction + "/bids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.totalItems").value(3))
                // EN: Newest first — the most recent offer is the one the page leads with.
                // VI: Mới nhất trước — lượt gần nhất là thứ trang hiển thị đầu tiên.
                .andExpect(jsonPath("$.data.items[0].amount").value(11000000))
                .andExpect(jsonPath("$.data.items[1].amount").value(10500000))
                .andExpect(jsonPath("$.data.items[2].amount").value(10000000));
    }

    @Test
    void namesAreMaskedForEveryone() throws Exception {
        String seller = tokenFor("hist.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("hist.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String pin = tokenFor("hist.pin2@nexbid.com", "Pinnacle Buyer", RoleName.BUYER);
        String alex = tokenFor("hist.alex2@nexbid.com", "Alexander Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Masked lot");

        bid(auction, alex, "10000000");
        bid(auction, pin, "10500000");

        // EN: Spec §37 prints exactly this. A full name here would hand a rival someone to look up.
        // VI: Spec §37 in đúng như vậy. Để nguyên họ tên ở đây là đưa cho đối thủ một người để tra cứu.
        mockMvc.perform(get("/api/auctions/" + auction + "/bids"))
                .andExpect(jsonPath("$.data.items[0].bidderMask").value("pin***"))
                .andExpect(jsonPath("$.data.items[1].bidderMask").value("ale***"))
                .andExpect(jsonPath("$.data.items[0].bidderMask", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Pinnacle"))));
    }

    @Test
    void theHistoryNeverCarriesABidderId() throws Exception {
        String seller = tokenFor("hist.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String admin = tokenFor("hist.admin3@nexbid.com", "Third Admin", RoleName.ADMIN);
        String buyer = tokenFor("hist.buyer3@nexbid.com", "Third Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "No ids lot");

        bid(auction, buyer, "10000000");

        String body = mockMvc.perform(get("/api/auctions/" + auction + "/bids"))
                .andReturn().getResponse().getContentAsString();

        String bidderId = jdbc.queryForObject(
                "SELECT bidder_id::text FROM bids WHERE auction_id = ?::uuid", String.class, auction);

        // EN: Masking the name is pointless if the raw id travels beside it — one lookup undoes it.
        // VI: Che tên là vô nghĩa nếu id gốc vẫn đi kèm — chỉ một lần tra là lộ.
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(bidderId);
    }

    @Test
    void aReaderSeesWhichLinesAreTheirOwn() throws Exception {
        String seller = tokenFor("hist.seller4@nexbid.com", "Fourth Seller", RoleName.SELLER);
        String admin = tokenFor("hist.admin4@nexbid.com", "Fourth Admin", RoleName.ADMIN);
        String me = tokenFor("hist.me4@nexbid.com", "Mine Buyer", RoleName.BUYER);
        String other = tokenFor("hist.other4@nexbid.com", "Other Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Mine lot");

        bid(auction, other, "10000000");
        bid(auction, me, "10500000");

        mockMvc.perform(get("/api/auctions/" + auction + "/bids")
                        .header("Authorization", "Bearer " + me))
                .andExpect(jsonPath("$.data.items[0].mine").value(true))
                .andExpect(jsonPath("$.data.items[1].mine").value(false));

        // EN: Without a token nobody owns any line — the same list, read by a stranger.
        // VI: Không có token thì không dòng nào là của ai — vẫn danh sách đó, nhìn bằng mắt người lạ.
        mockMvc.perform(get("/api/auctions/" + auction + "/bids"))
                .andExpect(jsonPath("$.data.items[0].mine").value(false))
                .andExpect(jsonPath("$.data.items[1].mine").value(false));
    }

    @Test
    void anEmptyLotReturnsAnEmptyPageNotAnError() throws Exception {
        String seller = tokenFor("hist.seller5@nexbid.com", "Fifth Seller", RoleName.SELLER);
        String admin = tokenFor("hist.admin5@nexbid.com", "Fifth Admin", RoleName.ADMIN);
        String auction = openLot(seller, admin, "Quiet lot");

        mockMvc.perform(get("/api/auctions/" + auction + "/bids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test
    void theListIsPagedSoOneLotCannotReturnTheWholeTable() throws Exception {
        String seller = tokenFor("hist.seller6@nexbid.com", "Sixth Seller", RoleName.SELLER);
        String admin = tokenFor("hist.admin6@nexbid.com", "Sixth Admin", RoleName.ADMIN);
        // EN: Three bidders taking turns: one person placing 25 bids in a few seconds is exactly what the
        //     bid rate limit (guide §35) stops, and this test is about paging, not about that.
        // VI: Ba người thay phiên nhau: một người đặt 25 lượt trong vài giây chính là thứ giới hạn tần suất
        //     (guide §35) chặn lại, mà test này nói về phân trang chứ không phải chuyện đó.
        List<String> buyers = List.of(
                tokenFor("hist.buyer6a@nexbid.com", "Sixth Buyer A", RoleName.BUYER),
                tokenFor("hist.buyer6b@nexbid.com", "Sixth Buyer B", RoleName.BUYER),
                tokenFor("hist.buyer6c@nexbid.com", "Sixth Buyer C", RoleName.BUYER));
        String auction = openLot(seller, admin, "Long lot");

        for (int i = 0; i < 25; i++) {
            bid(auction, buyers.get(i % 3), String.valueOf(10000000 + i * 500000L));
        }

        mockMvc.perform(get("/api/auctions/" + auction + "/bids"))
                .andExpect(jsonPath("$.data.items.length()").value(20))
                .andExpect(jsonPath("$.data.totalItems").value(25))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(get("/api/auctions/" + auction + "/bids?page=2"))
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.page").value(2));

        // EN: A caller asking for ten thousand rows gets the cap, not the table.
        // VI: Người gọi đòi mười nghìn dòng sẽ nhận được mức trần, không phải cả bảng.
        mockMvc.perform(get("/api/auctions/" + auction + "/bids?size=10000"))
                .andExpect(jsonPath("$.data.pageSize").value(100));
    }

    @Test
    void anUnapprovedLotHasNoPublicHistory() throws Exception {
        String seller = tokenFor("hist.seller7@nexbid.com", "Seventh Seller", RoleName.SELLER);
        String admin = tokenFor("hist.admin7@nexbid.com", "Seventh Admin", RoleName.ADMIN);
        String buyer = tokenFor("hist.buyer7@nexbid.com", "Seventh Buyer", RoleName.BUYER);
        String auction = openLot(seller, admin, "Hidden later");

        bid(auction, buyer, "10000000");

        jdbc.update("UPDATE auctions SET status = 'PENDING_APPROVAL' WHERE id = ?::uuid", auction);

        // EN: The detail page already hides this lot. If its history stayed readable, the bids would
        //     confirm the id is real — which is the thing hiding it was for.
        // VI: Trang chi tiết đã giấu lô này. Nếu lịch sử vẫn đọc được thì chính các lượt trả giá xác nhận
        //     id đó có thật — đúng cái điều mà việc giấu nhằm ngăn.
        mockMvc.perform(get("/api/auctions/" + auction + "/bids"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }

    @Test
    void anUnknownLotIs404() throws Exception {
        mockMvc.perform(get("/api/auctions/" + UUID.randomUUID() + "/bids"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }
}
