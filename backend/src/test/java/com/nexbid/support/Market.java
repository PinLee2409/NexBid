package com.nexbid.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.nexbid.auction.AuctionService;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * EN: The steps most API tests repeat: an account with a role, a product, a lot opened for bidding, a close.
 * VI: Các bước mà hầu hết test API lặp lại: tài khoản có vai trò, sản phẩm, lô mở trả giá, đóng lô.
 */
public final class Market {

    public record Account(UUID id, String email, String token) {
    }

    private final MockMvc mockMvc;
    private final UserService users;
    private final AuctionService auctions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public Market(MockMvc mockMvc, UserService users, AuctionService auctions, JdbcTemplate jdbc, ObjectMapper json) {
        this.mockMvc = mockMvc;
        this.users = users;
        this.auctions = auctions;
        this.jdbc = jdbc;
        this.json = json;
    }

    public Account account(String tag, RoleName role) throws Exception {
        return account(tag, role, null);
    }

    /** EN: {@code language} goes with the sign-up, as the page sends it. / VI: {@code language} gửi kèm lúc đăng ký, như trang gửi. */
    public Account account(String tag, RoleName role, String language) throws Exception {
        String email = tag + "." + UUID.randomUUID() + "@nexbid.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Tester %s","email":"%s","password":"supersecret"%s}
                """.formatted(tag, email, language == null ? "" : ",\"language\":\"" + language + "\"")))
                .andExpect(status().isCreated());
        if (role != RoleName.BUYER) {
            users.grantRole(email, role);
        }
        return new Account(users.findByEmail(email).orElseThrow().id(), email, login(email));
    }

    /** EN: A fresh token, carrying the roles as they are now. / VI: Token mới, mang các vai trò như hiện tại. */
    public String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"supersecret"}
                        """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("data").get("accessToken").asString();
    }

    public ResultActions as(Account who, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + who.token()));
    }

    public JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");
    }

    public String categoryId() throws Exception {
        return data(mockMvc.perform(get("/api/categories"))).get(0).get("id").asString();
    }

    public String product(Account seller, String name) throws Exception {
        return data(as(seller, post("/api/seller/products").contentType(MediaType.APPLICATION_JSON).content("""
                {"name":"%s","description":"A long description of the item.",
                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                """.formatted(name, categoryId())))).get("id").asString();
    }

    /** EN: Extra JSON fields go in {@code extra}, e.g. "\"buyNowPrice\":20000000". / VI: Trường JSON thêm đặt trong {@code extra}. */
    public String draft(Account seller, String productId, long startingPrice, String extra) throws Exception {
        String body = """
                {"productId":"%s","startingPrice":%d,"minimumIncrement":500000,"startTime":"%s","endTime":"%s"%s}
                """.formatted(productId, startingPrice, Instant.now().plus(Duration.ofMinutes(30)),
                Instant.now().plus(Duration.ofHours(4)), extra == null || extra.isBlank() ? "" : "," + extra);
        return data(as(seller, post("/api/seller/auctions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())).get("id").asString();
    }

    /** EN: Submitted, approved and opened at once. / VI: Gửi, duyệt và mở ngay. */
    public String openLot(Account seller, Account admin, String name, long startingPrice, String extra)
            throws Exception {
        String id = draft(seller, product(seller, name), startingPrice, extra);
        as(seller, post("/api/seller/auctions/" + id + "/submit")).andExpect(status().isOk());
        as(admin, post("/api/admin/auctions/" + id + "/approve")).andExpect(status().isOk());
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                id);
        return id;
    }

    public ResultActions bid(Account bidder, String lotId, long amount) throws Exception {
        return as(bidder, post("/api/auctions/" + lotId + "/bids").contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + "}"));
    }

    public void close(String lotId) {
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", lotId);
        auctions.endDueAuctions(Instant.now(), 200);
    }

    public String column(String table, String column, String id) {
        return jdbc.queryForObject("SELECT " + column + "::text FROM " + table + " WHERE id = ?::uuid", String.class,
                id);
    }

    public void saveBankAccount(Account who) throws Exception {
        as(who, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/users/me/bank-account")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bankName\":\"Vietcombank\",\"accountNumber\":\"0123456789\",\"accountHolder\":\"Test\"}"))
                .andExpect(status().isOk());
    }
}
