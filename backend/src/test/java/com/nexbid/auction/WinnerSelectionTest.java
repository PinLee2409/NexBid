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
 * EN: Choosing the winner (guide §27, spec §16). Decided once, by the server, at the close — and the
 *     tests below check both who it picks and who gets to find out.
 * VI: Chọn người thắng (guide §27, spec §16). Server quyết định một lần, lúc đóng phiên — và các test dưới
 *     đây kiểm cả việc nó chọn ai lẫn việc ai được phép biết.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class WinnerSelectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuctionService auctions;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private record Lot(String productId, String auctionId) {
    }

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

    private String idOf(String token) throws Exception {
        String body = mockMvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    private String newAuction(String seller, String productId) throws Exception {
        String body = mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId,
                                Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    /** EN: Approved and open for bidding. / VI: Đã duyệt và đang mở nhận trả giá. */
    private Lot activeLot(String seller, String admin, String name) throws Exception {
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

        String auctionId = newAuction(seller, productId);

        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit")
                .header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve")
                .header("Authorization", "Bearer " + admin));

        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' "
                + "WHERE id = ?::uuid", auctionId);

        return new Lot(productId, auctionId);
    }

    private void bid(String auctionId, String token, String amount) throws Exception {
        mockMvc.perform(post("/api/auctions/" + auctionId + "/bids")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    private void close(String auctionId) {
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", auctionId);
        auctions.endDueAuctions(Instant.now(), 200);
    }

    private String winnerOf(String auctionId) {
        return jdbc.queryForObject(
                "SELECT winner_id::text FROM auctions WHERE id = ?::uuid", String.class, auctionId);
    }

    @Test
    void theHighestBidderWinsAtTheLastPrice() throws Exception {
        String seller = tokenFor("win.seller1@nexbid.com", "Win Seller", RoleName.SELLER);
        String admin = tokenFor("win.admin1@nexbid.com", "Win Admin", RoleName.ADMIN);
        String pin = tokenFor("win.pin1@nexbid.com", "Pinnacle Buyer", RoleName.BUYER);
        String alex = tokenFor("win.alex1@nexbid.com", "Alexander Buyer", RoleName.BUYER);
        Lot lot = activeLot(seller, admin, "Contested lot");

        bid(lot.auctionId(), pin, "10000000");
        bid(lot.auctionId(), alex, "10500000");
        bid(lot.auctionId(), pin, "11000000");
        bid(lot.auctionId(), alex, "12000000");

        close(lot.auctionId());

        assertThat(winnerOf(lot.auctionId())).isEqualTo(idOf(alex));

        // EN: The winner is the author of the top bid record, not just whoever the counter remembers.
        // VI: Người thắng là chủ nhân của bản ghi lượt trả giá cao nhất, không chỉ là cái tên bộ đếm nhớ.
        String topBidder = jdbc.queryForObject(
                "SELECT bidder_id::text FROM bids WHERE auction_id = ?::uuid ORDER BY amount DESC LIMIT 1",
                String.class, lot.auctionId());
        assertThat(winnerOf(lot.auctionId())).isEqualTo(topBidder);

        mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(jsonPath("$.data.auction.currentPrice").value(12000000));
    }

    @Test
    void nobodyIsTheWinnerWhileTheLotIsStillRunning() throws Exception {
        String seller = tokenFor("win.seller2@nexbid.com", "Second Seller", RoleName.SELLER);
        String admin = tokenFor("win.admin2@nexbid.com", "Second Admin", RoleName.ADMIN);
        String buyer = tokenFor("win.buyer2@nexbid.com", "Second Buyer", RoleName.BUYER);
        Lot lot = activeLot(seller, admin, "Running lot");

        bid(lot.auctionId(), buyer, "10000000");

        // EN: Leading is not winning. Until the close there is a leader, and no winner.
        // VI: Dẫn đầu chưa phải là thắng. Trước lúc đóng chỉ có người dẫn, chưa có người thắng.
        assertThat(winnerOf(lot.auctionId())).isNull();

        mockMvc.perform(get("/api/users/me/wins").header("Authorization", "Bearer " + buyer))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void aLotThatEndsWithNoBidsHasNoWinnerAndItsProductCanBeListedAgain() throws Exception {
        String seller = tokenFor("win.seller3@nexbid.com", "Third Seller", RoleName.SELLER);
        String admin = tokenFor("win.admin3@nexbid.com", "Third Admin", RoleName.ADMIN);
        Lot lot = activeLot(seller, admin, "Unsold lot");

        close(lot.auctionId());

        assertThat(winnerOf(lot.auctionId())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid",
                String.class, lot.auctionId())).isEqualTo("ENDED");

        // EN: Without this the unsold item is frozen for good: the seller can neither relist nor delete it.
        // VI: Thiếu bước này, món hàng không bán được bị đóng băng mãi: người bán không đăng lại, cũng
        //     không xoá được.
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?::uuid",
                String.class, lot.productId())).isEqualTo("AVAILABLE");

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":9000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(lot.productId(),
                                Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andExpect(status().isCreated());
    }

    @Test
    void aSoldProductStaysWithItsAuction() throws Exception {
        String seller = tokenFor("win.seller4@nexbid.com", "Fourth Seller", RoleName.SELLER);
        String admin = tokenFor("win.admin4@nexbid.com", "Fourth Admin", RoleName.ADMIN);
        String buyer = tokenFor("win.buyer4@nexbid.com", "Fourth Buyer", RoleName.BUYER);
        Lot lot = activeLot(seller, admin, "Sold lot");

        bid(lot.auctionId(), buyer, "10000000");
        close(lot.auctionId());

        // EN: Someone won it and has not paid yet. The seller must not be able to sell it twice.
        // VI: Đã có người thắng mà chưa thanh toán. Người bán không được bán nó lần thứ hai.
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?::uuid",
                String.class, lot.productId())).isEqualTo("IN_AUCTION");

        mockMvc.perform(post("/api/seller/auctions")
                        .header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","startingPrice":9000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(lot.productId(),
                                Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andExpect(status().isConflict());
    }

    @Test
    void theDatabaseItselfRefusesASecondSaleOfAWonProduct() throws Exception {
        String seller = tokenFor("win.seller5@nexbid.com", "Fifth Seller", RoleName.SELLER);
        String admin = tokenFor("win.admin5@nexbid.com", "Fifth Admin", RoleName.ADMIN);
        String buyer = tokenFor("win.buyer5@nexbid.com", "Fifth Buyer", RoleName.BUYER);
        Lot lot = activeLot(seller, admin, "Guarded lot");

        bid(lot.auctionId(), buyer, "10000000");
        close(lot.auctionId());

        // EN: Straight past the application: the partial unique index rewritten in V7 is the real guard.
        // VI: Đi thẳng qua mặt ứng dụng: index duy nhất có điều kiện viết lại ở V7 mới là chốt thật.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO auctions (id, product_id, seller_id, starting_price, current_price,
                            minimum_increment, start_time, end_time, status, created_at, updated_at)
                        SELECT gen_random_uuid(), product_id, seller_id, 1, 1, 1,
                            now() + interval '1 hour', now() + interval '2 hours', 'DRAFT', now(), now()
                        FROM auctions WHERE id = ?::uuid
                        """, lot.auctionId()))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void myWinsListsExactlyTheLotsTheCallerWon() throws Exception {
        String seller = tokenFor("win.seller6@nexbid.com", "Sixth Seller", RoleName.SELLER);
        String admin = tokenFor("win.admin6@nexbid.com", "Sixth Admin", RoleName.ADMIN);
        String winner = tokenFor("win.winner6@nexbid.com", "Sixth Winner", RoleName.BUYER);
        String loser = tokenFor("win.loser6@nexbid.com", "Sixth Loser", RoleName.BUYER);

        Lot first = activeLot(seller, admin, "First prize");
        Lot second = activeLot(seller, admin, "Second prize");
        Lot lost = activeLot(seller, admin, "Someone else's prize");

        bid(first.auctionId(), loser, "10000000");
        bid(first.auctionId(), winner, "10500000");
        bid(second.auctionId(), winner, "10000000");
        bid(lost.auctionId(), winner, "10000000");
        bid(lost.auctionId(), loser, "10500000");

        close(first.auctionId());
        close(second.auctionId());
        close(lost.auctionId());

        mockMvc.perform(get("/api/users/me/wins").header("Authorization", "Bearer " + winner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].auction.id",
                        org.hamcrest.Matchers.containsInAnyOrder(first.auctionId(), second.auctionId())))
                .andExpect(jsonPath("$.data[*].product.name",
                        org.hamcrest.Matchers.containsInAnyOrder("First prize", "Second prize")));

        mockMvc.perform(get("/api/users/me/wins").header("Authorization", "Bearer " + loser))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].auction.id").value(lost.auctionId()))
                .andExpect(jsonPath("$.data[0].auction.currentPrice").value(10500000));

        // EN: Selling is not winning.
        // VI: Bán được hàng không phải là thắng đấu giá.
        mockMvc.perform(get("/api/users/me/wins").header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void myWinsNeedsASignedInCaller() throws Exception {
        mockMvc.perform(get("/api/users/me/wins"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"));
    }

    @Test
    void thePublicNeverSeesTheWinnersAccountId() throws Exception {
        String seller = tokenFor("win.seller7@nexbid.com", "Seventh Seller", RoleName.SELLER);
        String admin = tokenFor("win.admin7@nexbid.com", "Seventh Admin", RoleName.ADMIN);
        String buyer = tokenFor("win.buyer7@nexbid.com", "Seventh Buyer", RoleName.BUYER);
        Lot lot = activeLot(seller, admin, "Discreet lot");

        bid(lot.auctionId(), buyer, "10000000");
        close(lot.auctionId());

        String winnerId = idOf(buyer);

        // EN: The bid history masks every name. Publishing the raw id beside the result would let anyone
        //     who has seen that id elsewhere — say, on a seller's listing — put a name to the winner.
        // VI: Lịch sử trả giá che mọi cái tên. Công bố id gốc cạnh kết quả sẽ cho bất kỳ ai từng thấy id đó
        //     ở chỗ khác — chẳng hạn trên một tin đăng bán — gắn được tên cho người thắng.
        String detail = mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(jsonPath("$.data.auction.status").value("ENDED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain(winnerId);

        String catalogue = mockMvc.perform(get("/api/auctions?status=ENDED&size=50"))
                .andReturn().getResponse().getContentAsString();
        assertThat(catalogue).contains(lot.auctionId()).doesNotContain(winnerId);

        // EN: The seller is a party to the sale and does see who bought.
        // VI: Người bán là một bên của giao dịch nên được biết ai đã mua.
        mockMvc.perform(get("/api/seller/auctions/" + lot.auctionId())
                        .header("Authorization", "Bearer " + seller))
                .andExpect(jsonPath("$.data.winnerId").value(winnerId));
    }

    @Test
    void aLotWhoseWholeWindowWasMissedEndsWithNoWinnerAndReleasesItsProduct() throws Exception {
        String seller = tokenFor("win.seller8@nexbid.com", "Eighth Seller", RoleName.SELLER);
        String admin = tokenFor("win.admin8@nexbid.com", "Eighth Admin", RoleName.ADMIN);
        Lot lot = activeLot(seller, admin, "Missed lot");

        jdbc.update("UPDATE auctions SET status = 'SCHEDULED', start_time = now() - interval '5 hours', "
                + "end_time = now() - interval '4 hours' WHERE id = ?::uuid", lot.auctionId());
        auctions.endDueAuctions(Instant.now(), 200);

        assertThat(winnerOf(lot.auctionId())).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?::uuid",
                String.class, lot.productId())).isEqualTo("AVAILABLE");
    }
}
