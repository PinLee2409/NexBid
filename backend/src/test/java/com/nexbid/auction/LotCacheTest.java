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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: The lot cache (guide §34). Each test changes something behind the cache's back and checks which
 *     version the page shows — the only way to prove what is cached and what is read fresh.
 * VI: Cache của lô (guide §34). Mỗi test thay đổi một thứ sau lưng cache rồi xem trang hiện phiên bản nào —
 *     cách duy nhất để chứng minh cái gì được cache và cái gì luôn đọc mới.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class LotCacheTest {

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
    private StringRedisTemplate redis;

    @Autowired
    private ObjectMapper objectMapper;

    private record Lot(String auctionId, String productId, String seller) {
    }

    private String tokenFor(String email, String name, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"%s","email":"%s","password":"supersecret"}
                """.formatted(name, email)));
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"supersecret"}
                        """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    /** EN: An open lot with one photo. / VI: Một lô đang mở có một ảnh. */
    private Lot openLot(String tag, String name) throws Exception {
        String seller = tokenFor("cache.seller." + tag + "@nexbid.com", "Seller " + tag, RoleName.SELLER);
        String admin = tokenFor("cache.admin." + tag + "@nexbid.com", "Admin " + tag, RoleName.ADMIN);

        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"%s","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(name, categoryId)))
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();
        addPhoto(seller, productId);

        String auctionBody = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();
        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auctionId);

        return new Lot(auctionId, productId, seller);
    }

    private void addPhoto(String seller, String productId) throws Exception {
        mockMvc.perform(multipart("/api/seller/products/" + productId + "/images")
                        .file(new MockMultipartFile("files", "photo.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + seller))
                .andExpect(status().is2xxSuccessful());
    }

    private String key(Lot lot) {
        return LotCache.keyOf(UUID.fromString(lot.auctionId()));
    }

    /** EN: Changes the product name in the database only, behind the cache's back. / VI: Chỉ đổi tên sản phẩm trong database, sau lưng cache. */
    private void renameBehindTheCache(Lot lot, String name) {
        jdbc.update("UPDATE products SET name = ? WHERE id = ?::uuid", name, lot.productId());
    }

    @Test
    void theLotPageFillsTheCacheAndThenReadsItsStaticPartsFromIt() throws Exception {
        Lot lot = openLot("fill", "Leica M6");
        redis.delete(key(lot));

        mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product.name").value("Leica M6"));

        assertThat(redis.hasKey(key(lot))).isTrue();
        // EN: Every card expires on its own, so a missed eviction heals itself. / VI: Mọi thẻ đều tự hết hạn, nên lỡ quên xoá cũng tự lành.
        assertThat(redis.getExpire(key(lot))).isBetween(1L, 600L);

        renameBehindTheCache(lot, "Renamed behind the cache");

        // EN: Still the cached name: this read never went to the products table.
        // VI: Vẫn là tên trong cache: lần đọc này không hề chạm tới bảng products.
        mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(jsonPath("$.data.product.name").value("Leica M6"));

        redis.delete(key(lot));
        mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(jsonPath("$.data.product.name").value("Renamed behind the cache"));
    }

    @Test
    void aBidIsNeverStaleBecauseNothingABidChangesIsCached() throws Exception {
        Lot lot = openLot("bid", "Rolex Datejust");
        String buyer = tokenFor("cache.buyer.bid@nexbid.com", "Cache Buyer", RoleName.BUYER);

        mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(jsonPath("$.data.auction.currentPrice").value(10000000));
        String cardBefore = redis.opsForValue().get(key(lot));

        mockMvc.perform(post("/api/auctions/" + lot.auctionId() + "/bids").header("Authorization", "Bearer " + buyer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":12000000}"))
                .andExpect(status().isCreated());

        // EN: Guide §34: "invalidate on bid". Here there is nothing to invalidate — the card did not change,
        //     and the page still shows the new price at once, because price is read from the auction row.
        // VI: Guide §34: "bid xong thì xoá cache". Ở đây không có gì để xoá — thẻ không đổi, mà trang vẫn hiện
        //     giá mới ngay, vì giá được đọc từ dòng auction.
        assertThat(redis.opsForValue().get(key(lot))).isEqualTo(cardBefore);
        assertThat(cardBefore).doesNotContain("12000000").doesNotContain("currentPrice");

        mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(jsonPath("$.data.auction.currentPrice").value(12000000))
                .andExpect(jsonPath("$.data.auction.bidCount").value(1))
                .andExpect(jsonPath("$.data.minimumNextBid").value(12500000));
    }

    @Test
    void timeDependentFieldsAreWorkedOutOnEveryRequest() throws Exception {
        Lot lot = openLot("clock", "Clock lot");
        jdbc.update("UPDATE auctions SET status = 'SCHEDULED', start_time = now() + interval '1 hour' WHERE id = ?::uuid",
                lot.auctionId());

        String first = mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(jsonPath("$.data.openForBidding").value(false))
                .andReturn().getResponse().getContentAsString();

        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 minute' WHERE id = ?::uuid",
                lot.auctionId());
        Thread.sleep(5);

        String second = mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(jsonPath("$.data.openForBidding").value(true))
                .andReturn().getResponse().getContentAsString();

        // EN: serverTime is what every countdown measures against (guide §24); a cached one would stop the clock.
        // VI: serverTime là mốc mọi đồng hồ đếm ngược đo theo (guide §24); cache nó là làm đồng hồ đứng lại.
        String t1 = objectMapper.readTree(first).get("data").get("serverTime").asString();
        String t2 = objectMapper.readTree(second).get("data").get("serverTime").asString();
        assertThat(Instant.parse(t2)).isAfter(Instant.parse(t1));
    }

    @Test
    void theCatalogueUsesTheSameCardsAndLivePrices() throws Exception {
        Lot lot = openLot("list", "Catalogue camera");
        String buyer = tokenFor("cache.buyer.list@nexbid.com", "List Buyer", RoleName.BUYER);
        redis.delete(key(lot));

        mockMvc.perform(get("/api/auctions?size=50")).andExpect(status().isOk());
        assertThat(redis.hasKey(key(lot))).isTrue();

        renameBehindTheCache(lot, "Renamed in the catalogue");
        mockMvc.perform(post("/api/auctions/" + lot.auctionId() + "/bids").header("Authorization", "Bearer " + buyer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":11000000}"));

        String body = mockMvc.perform(get("/api/auctions?size=50")).andReturn().getResponse().getContentAsString();
        var card = objectMapper.readTree(body).get("data").get("items").valueStream()
                .filter(item -> item.get("auction").get("id").asString().equals(lot.auctionId()))
                .findFirst().orElseThrow();

        assertThat(card.get("product").get("name").asString()).isEqualTo("Catalogue camera");
        assertThat(card.get("auction").get("currentPrice").decimalValue()).isEqualByComparingTo("11000000");
    }

    @Test
    void changingThePhotosDropsTheCard() throws Exception {
        Lot lot = openLot("photos", "Photo lot");

        mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(jsonPath("$.data.images.length()").value(1));

        addPhoto(lot.seller(), lot.productId());

        // EN: Without the eviction this would still say 1 for the rest of the TTL.
        // VI: Nếu không xoá thẻ thì chỗ này vẫn ra 1 suốt thời gian còn lại của TTL.
        mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(jsonPath("$.data.images.length()").value(2));
    }

    @Test
    void aLotThatLeavesPublicViewIsNotServedFromItsCard() throws Exception {
        Lot lot = openLot("hidden", "Hidden lot");
        mockMvc.perform(get("/api/auctions/" + lot.auctionId())).andExpect(status().isOk());
        assertThat(redis.hasKey(key(lot))).isTrue();

        jdbc.update("UPDATE auctions SET status = 'PENDING_APPROVAL' WHERE id = ?::uuid", lot.auctionId());

        // EN: Visibility is decided on the live row; a warm card changes nothing.
        // VI: Quyền được xem quyết định trên dòng dữ liệu hiện tại; thẻ trong cache không thay đổi được điều đó.
        mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }

    @Test
    void aCardThatNoLongerReadsIsJustAMiss() throws Exception {
        Lot lot = openLot("garbage", "Sturdy lot");
        redis.opsForValue().set(key(lot), "{not json at all", Duration.ofMinutes(5));

        mockMvc.perform(get("/api/auctions/" + lot.auctionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product.name").value("Sturdy lot"));

        // EN: And it is rebuilt properly. / VI: Và được dựng lại đúng.
        assertThat(redis.opsForValue().get(key(lot))).contains("Sturdy lot");
    }
}
