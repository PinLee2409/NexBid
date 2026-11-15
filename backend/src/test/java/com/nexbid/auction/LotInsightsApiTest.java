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
 * EN: Lots like this one, visits counted once a day, and the seller's figures.
 * VI: Các lô giống lô này, lượt xem đếm mỗi ngày một lần, và số liệu của người bán.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class LotInsightsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Insight User","email":"%s","password":"supersecret"}
                """.formatted(email)));
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"supersecret"}
                """.formatted(email))).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    /** EN: An approved, open lot in the given category. / VI: Một lô đã duyệt, đang mở, trong danh mục cho trước. */
    /** EN: A category of the test's own, so other tests' lots stay out of it. / VI: Một danh mục riêng của test, để lô của test khác không lẫn vào. */
    private String category(String admin, String name) throws Exception {
        String body = mockMvc.perform(post("/api/admin/categories").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("id").asString();
    }

    private String openLot(String seller, String admin, String name, String categoryId) throws Exception {
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"name":"%s","description":"A long description of the item.",
                         "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                        """.formatted(name, categoryId))).andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();
        String auctionBody = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"productId":"%s","startingPrice":1000000,"minimumIncrement":100000,
                         "startTime":"%s","endTime":"%s"}
                        """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)),
                        Instant.now().plus(Duration.ofHours(4))))).andReturn().getResponse().getContentAsString();
        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auctionId);
        return auctionId;
    }

    @Test
    void similarLotsShareTheCategoryAndPutOtherSellersFirst() throws Exception {
        String seller = tokenFor("li.seller@nexbid.com", RoleName.SELLER);
        String other = tokenFor("li.other@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("li.admin@nexbid.com", RoleName.ADMIN);
        String mine = category(admin, "Insight clocks");
        String lot = openLot(seller, admin, "Insight anchor", mine);
        String sameSeller = openLot(seller, admin, "Insight same seller", mine);
        String otherSeller = openLot(other, admin, "Insight other seller", mine);
        String elsewhere = openLot(other, admin, "Insight elsewhere", category(admin, "Insight lamps"));

        String body = mockMvc.perform(get("/api/auctions/" + lot + "/similar"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var ids = objectMapper.readTree(body).get("data").findValuesAsString("id");
        assertThat(ids).contains(sameSeller, otherSeller).doesNotContain(lot, elsewhere);
        assertThat(ids.indexOf(otherSeller)).isLessThan(ids.indexOf(sameSeller));

        mockMvc.perform(get("/api/auctions/cards").param("ids", otherSeller, lot, otherSeller))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].auction.id").value(otherSeller));
        jdbc.update("UPDATE auctions SET status = 'REMOVED' WHERE id = ?::uuid", elsewhere);
        mockMvc.perform(get("/api/auctions/" + elsewhere + "/similar")).andExpect(status().isNotFound());
    }

    @Test
    void visitsCountOncePerViewerADayAndFeedTheSellersFigures() throws Exception {
        String seller = tokenFor("lv.seller@nexbid.com", RoleName.SELLER);
        String admin = tokenFor("lv.admin@nexbid.com", RoleName.ADMIN);
        String buyer = tokenFor("lv.buyer@nexbid.com", RoleName.BUYER);
        String lot = openLot(seller, admin, "Visited lot", category(admin, "Insight visits"));

        mockMvc.perform(post("/api/auctions/" + lot + "/views")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auctions/" + lot + "/views")).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/auctions/" + lot + "/views").header("Authorization", "Bearer " + buyer));
        mockMvc.perform(post("/api/auctions/" + lot + "/views").header("Authorization", "Bearer " + buyer));
        // EN: The seller's own visits are not counted. / VI: Người bán tự xem không được đếm.
        mockMvc.perform(post("/api/auctions/" + lot + "/views").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/auctions/" + lot + "/watch").header("Authorization", "Bearer " + buyer));
        mockMvc.perform(post("/api/auctions/" + lot + "/bids").header("Authorization", "Bearer " + buyer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000000}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/seller/auctions/stats").header("Authorization", "Bearer " + seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.views").value(2))
                .andExpect(jsonPath("$.data.lots[0].name").value("Visited lot"))
                .andExpect(jsonPath("$.data.lots[0].views").value(2))
                .andExpect(jsonPath("$.data.lots[0].watchers").value(1))
                .andExpect(jsonPath("$.data.lots[0].bidders").value(1))
                .andExpect(jsonPath("$.data.lots[0].bids").value(1))
                .andExpect(jsonPath("$.data.sellThrough").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(get("/api/seller/auctions/stats").header("Authorization", "Bearer " + buyer))
                .andExpect(status().isForbidden());
    }
}
