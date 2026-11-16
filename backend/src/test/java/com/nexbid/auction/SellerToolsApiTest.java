package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.Market;
import com.nexbid.support.Market.Account;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Search suggestions and past prices for buyers; relisting, vacations and listing templates for sellers.
 * VI: Gợi ý tìm kiếm và giá đã bán cho người mua; đăng lại, kỳ nghỉ và mẫu mô tả cho người bán.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class SellerToolsApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserService users;
    @Autowired
    private AuctionService auctions;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private Market market;
    private Account seller;
    private Account admin;

    @BeforeEach
    void setUp() throws Exception {
        market = new Market(mockMvc, users, auctions, jdbc, json);
        seller = market.account("tools.seller", RoleName.SELLER);
        admin = market.account("tools.admin", RoleName.ADMIN);
    }

    @Test
    void suggestionsIgnoreAccentsAndOfferASpelling() throws Exception {
        String tag = Long.toString(System.nanoTime(), 36);
        String lot = market.openLot(seller, admin, "Đồng hồ Zentrix " + tag, 10_000_000, null);

        mockMvc.perform(get("/api/auctions/suggest").param("q", "dong ho zentrix " + tag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lots[0].auctionId").value(lot))
                .andExpect(jsonPath("$.data.correction").isEmpty());
        mockMvc.perform(get("/api/auctions/suggest").param("q", "Tester tools.seller"))
                .andExpect(jsonPath("$.data.sellers[?(@.id == '" + seller.id() + "')]").exists());
        mockMvc.perform(get("/api/auctions/suggest").param("q", "z"))
                .andExpect(jsonPath("$.data.lots.length()").value(0));
        // EN: A typo finds nothing, but the near name comes back as a correction. / VI: Gõ sai không tìm thấy gì, nhưng tên gần đúng được trả về làm gợi ý sửa.
        mockMvc.perform(get("/api/auctions/suggest").param("q", "Zentrixx " + tag + "q"))
                .andExpect(jsonPath("$.data.correction").value(org.hamcrest.Matchers.containsString("Zentrix")));
    }

    @Test
    void pastSalesOfSimilarLots() throws Exception {
        String tag = Long.toString(System.nanoTime(), 36);
        Account buyer = market.account("tools.buyer", RoleName.BUYER);
        for (long price : new long[] {11_000_000, 12_000_000, 14_000_000}) {
            String sold = market.openLot(seller, admin, "Leica M6 " + tag + " " + price, 10_000_000, null);
            market.bid(buyer, sold, price).andExpect(status().isCreated());
            market.close(sold);
        }
        String lot = market.openLot(seller, admin, "Leica M6 " + tag + " body", 10_000_000, null);
        mockMvc.perform(get("/api/auctions/" + lot + "/price-history"))
                .andExpect(jsonPath("$.data.basis").value("SIMILAR"))
                .andExpect(jsonPath("$.data.sales.length()").value(3))
                .andExpect(jsonPath("$.data.median").value(12000000))
                .andExpect(jsonPath("$.data.low").value(11000000));
    }

    @Test
    void anUnsoldLotRelistsItselfCheaper() throws Exception {
        String lot = market.openLot(seller, admin, "Relisted lamp", 10_000_000,
                "\"autoRelist\":2,\"relistDropPercent\":10");
        market.close(lot);

        String next = jdbc.queryForObject("SELECT id::text FROM auctions WHERE relisted_from = ?::uuid", String.class, lot);
        assertThat(market.column("auctions", "status", next)).isEqualTo("SCHEDULED");
        assertThat(market.column("auctions", "starting_price", next)).isEqualTo("9000000.00");
        assertThat(market.column("auctions", "relist_left", next)).isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT p.status FROM products p JOIN auctions a ON a.product_id = p.id WHERE a.id = ?::uuid",
                String.class, next)).isEqualTo("IN_AUCTION");

        // EN: Away on vacation: the next close does not relist. / VI: Đang nghỉ: lần đóng sau không đăng lại.
        market.as(seller, put("/api/seller/vacation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"until\":\"" + Instant.now().plus(3, ChronoUnit.DAYS) + "\",\"note\":\"Back Monday\"}"))
                .andExpect(jsonPath("$.data.note").value("Back Monday"));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid", next);
        market.close(next);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auctions WHERE relisted_from = ?::uuid", Integer.class, next))
                .isZero();

        // EN: By hand, as a draft to check first. / VI: Bằng tay, thành bản nháp để kiểm trước.
        market.as(seller, post("/api/seller/auctions/" + next + "/relist").contentType(MediaType.APPLICATION_JSON)
                .content("{\"dropPercent\":20}"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.startingPrice").value(7200000))
                .andExpect(jsonPath("$.data.relistedFrom").value(next));
        market.as(seller, post("/api/seller/auctions/" + next + "/relist"))
                .andExpect(jsonPath("$.code").value("RELIST_NOT_ALLOWED"));
    }

    @Test
    void aVacationPausesSubmissionsAndShowsOnThePage() throws Exception {
        String draft = market.draft(seller, market.product(seller, "Vacation draft"), 10_000_000, null);
        market.as(seller, put("/api/seller/vacation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"until\":\"" + Instant.now().plus(200, ChronoUnit.DAYS) + "\"}"))
                .andExpect(jsonPath("$.code").value("VACATION_INVALID"));
        market.as(seller, put("/api/seller/vacation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"until\":\"" + Instant.now().plus(5, ChronoUnit.DAYS) + "\",\"note\":\"At a fair\"}"))
                .andExpect(status().isOk());

        market.as(seller, post("/api/seller/auctions/" + draft + "/submit"))
                .andExpect(jsonPath("$.code").value("SELLER_ON_VACATION"));
        mockMvc.perform(get("/api/sellers/" + seller.id() + "/profile"))
                .andExpect(jsonPath("$.data.awayNote").value("At a fair"))
                .andExpect(jsonPath("$.data.awayUntil").isNotEmpty());

        market.as(seller, put("/api/seller/vacation").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.data.until").isEmpty());
        market.as(seller, post("/api/seller/auctions/" + draft + "/submit")).andExpect(status().isOk());
    }

    @Test
    void templatesAreTheSellersOwn() throws Exception {
        String body = """
                {"name":"Watches","description":"Serviced 2024. 7-day returns.","categoryId":"%s","condition":"GOOD",
                 "parcel":{"weightGrams":400,"lengthCm":15,"widthCm":12,"heightCm":10}}
                """.formatted(market.categoryId());
        String id = market.data(market.as(seller, post("/api/seller/templates").contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk())).get("id").asString();
        market.as(seller, post("/api/seller/templates").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(jsonPath("$.code").value("TEMPLATE_INVALID"));
        market.as(seller, get("/api/seller/templates")).andExpect(jsonPath("$.data[0].parcel.weightGrams").value(400));

        Account other = market.account("tools.other", RoleName.SELLER);
        market.as(other, get("/api/seller/templates")).andExpect(jsonPath("$.data.length()").value(0));
        market.as(other, delete("/api/seller/templates/" + id)).andExpect(status().isNotFound());
        market.as(seller, put("/api/seller/templates/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("Watches", "Watches 2"))).andExpect(jsonPath("$.data.name").value("Watches 2"));
        market.as(seller, delete("/api/seller/templates/" + id)).andExpect(status().isOk());
    }
}
