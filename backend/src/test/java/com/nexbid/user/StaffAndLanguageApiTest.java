package com.nexbid.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.auction.AuctionService;
import com.nexbid.support.Market;
import com.nexbid.support.Market.Account;
import com.nexbid.support.TestInfrastructure;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Staff roles open only their part of the console; emails follow the language a person chose; the conversion
 *     funnel counts each step.
 * VI: Vai trò nhân viên chỉ mở phần của mình trong trang quản trị; email theo ngôn ngữ người dùng chọn; phễu chuyển đổi
 *     đếm từng bước.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class StaffAndLanguageApiTest {

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
    private Account admin;

    @BeforeEach
    void setUp() throws Exception {
        market = new Market(mockMvc, users, auctions, jdbc, json);
        admin = market.account("staff.admin", RoleName.ADMIN);
    }

    private Account withToken(Account who) throws Exception {
        return new Account(who.id(), who.email(), market.login(who.email()));
    }

    @Test
    void eachStaffRoleOpensItsOwnPart() throws Exception {
        Account clerk = market.account("staff.finance", RoleName.BUYER);
        market.as(clerk, get("/api/admin/payouts/due")).andExpect(status().isForbidden());

        market.as(admin, post("/api/admin/staff").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + clerk.email() + "\",\"roles\":[\"FINANCE\"]}"))
                .andExpect(jsonPath("$.data.roles").value(org.hamcrest.Matchers.hasItem("FINANCE")));
        market.as(admin, put("/api/admin/staff/" + clerk.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"ADMIN\"]}")).andExpect(jsonPath("$.code").value("STAFF_ROLES_INVALID"));
        market.as(admin, put("/api/admin/staff/" + admin.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[]}")).andExpect(jsonPath("$.code").value("STAFF_ROLES_INVALID"));

        Account finance = withToken(clerk);
        market.as(finance, get("/api/admin/payouts/due")).andExpect(status().isOk());
        market.as(finance, get("/api/admin/deposits")).andExpect(status().isOk());
        market.as(finance, get("/api/admin/analytics")).andExpect(status().isOk());
        market.as(finance, get("/api/admin/auctions")).andExpect(status().isForbidden());
        market.as(finance, get("/api/admin/staff")).andExpect(status().isForbidden());
        market.as(finance, get("/api/admin/audit-logs")).andExpect(status().isForbidden());

        market.as(admin, put("/api/admin/staff/" + clerk.id()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[\"MODERATOR\"]}")).andExpect(status().isOk());
        Account moderator = withToken(clerk);
        market.as(moderator, get("/api/admin/auctions/pending")).andExpect(status().isOk());
        market.as(moderator, get("/api/admin/payouts/due")).andExpect(status().isForbidden());
        market.as(admin, get("/api/admin/staff"))
                .andExpect(jsonPath("$.data[?(@.id == '" + clerk.id() + "')].roles[*]").value(
                        org.hamcrest.Matchers.hasItem("MODERATOR")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE action = 'STAFF_ROLES_CHANGED' AND entity_id = ?",
                Integer.class, clerk.id())).isEqualTo(2);
    }

    @Test
    void emailsFollowTheChosenLanguage() throws Exception {
        Account english = market.account("lang.en", RoleName.BUYER, "en");
        Account both = market.account("lang.both", RoleName.BUYER);
        String welcome = "SELECT subject FROM email_outbox WHERE user_id = ? AND kind = 'WELCOME'";
        assertThat(jdbc.queryForObject(welcome, String.class, english.id())).isEqualTo("Welcome to NexBid");
        assertThat(jdbc.queryForObject("SELECT html FROM email_outbox WHERE user_id = ? AND kind = 'WELCOME'",
                String.class, english.id())).contains("lang=\"en\"").doesNotContain("Chào mừng");
        assertThat(jdbc.queryForObject(welcome, String.class, both.id())).contains(" · ");

        market.as(both, put("/api/users/me/email-preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"outbid\":true,\"language\":\"vi\"}")).andExpect(jsonPath("$.data.language").value("vi"));
        market.as(both, get("/api/users/me/email-preferences")).andExpect(jsonPath("$.data.language").value("vi"));
        market.as(both, put("/api/users/me/email-preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"outbid\":true,\"language\":\"fr\"}")).andExpect(jsonPath("$.data.language").value("vi"));
    }

    @Test
    void theFunnelCountsEachStep() throws Exception {
        Account seller = market.account("funnel.seller", RoleName.SELLER);
        Account buyer = market.account("funnel.buyer", RoleName.BUYER);
        String before = market.data(market.as(admin, get("/api/admin/analytics/funnel").param("days", "7")))
                .get("total").toString();
        int closed = json.readTree(before).get("closed").asInt();
        int won = json.readTree(before).get("won").asInt();

        String sold = market.openLot(seller, admin, "Funnel sold", 10_000_000, null);
        market.bid(buyer, sold, 10_000_000).andExpect(status().isCreated());
        market.close(sold);
        market.close(market.openLot(seller, admin, "Funnel unsold", 10_000_000, null));

        market.as(admin, get("/api/admin/analytics/funnel").param("days", "7"))
                .andExpect(jsonPath("$.data.total.closed").value(closed + 2))
                .andExpect(jsonPath("$.data.total.won").value(won + 1))
                .andExpect(jsonPath("$.data.categories[0].slug").isNotEmpty());
    }
}
