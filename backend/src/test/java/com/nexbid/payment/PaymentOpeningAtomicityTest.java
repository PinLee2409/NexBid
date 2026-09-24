package com.nexbid.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.auction.AuctionService;
import com.nexbid.support.PostgresTestcontainer;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: A winner never exists without a payment (spec §12). If the payment cannot be written, the close is
 *     undone and the next tick tries again — never a lot that is ENDED with nobody asked to pay.
 * VI: Không bao giờ có người thắng mà thiếu khoản thanh toán (spec §12). Nếu không ghi được thanh toán, việc
 *     đóng phiên bị hoàn tác và nhịp sau thử lại — không bao giờ có lô ENDED mà chẳng ai được yêu cầu trả tiền.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class PaymentOpeningAtomicityTest {

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

    @MockitoSpyBean
    private PaymentService payments;

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

    @Test
    void ifThePaymentCannotBeOpenedTheCloseIsUndoneAndRetried() throws Exception {
        String seller = tokenFor("atom.seller@nexbid.com", "Atom Seller", RoleName.SELLER);
        String admin = tokenFor("atom.admin@nexbid.com", "Atom Admin", RoleName.ADMIN);
        String buyer = tokenFor("atom.buyer@nexbid.com", "Atom Buyer", RoleName.BUYER);

        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"Atomic lot","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(categoryId)))
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();
        String auctionBody = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":10000000,"minimumIncrement":500000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();
        String auction = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auction + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auction + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auction);
        mockMvc.perform(post("/api/auctions/" + auction + "/bids").header("Authorization", "Bearer " + buyer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000000}"));
        jdbc.update("UPDATE auctions SET end_time = now() - interval '1 second' WHERE id = ?::uuid", auction);

        doThrow(new IllegalStateException("payment store is down"))
                .when(payments).openFor(any(), any(), any(), any());

        assertThatThrownBy(() -> auctions.endDueAuctions(Instant.now(), 200))
                .hasMessageContaining("payment store is down");

        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, auction))
                .isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT winner_id FROM auctions WHERE id = ?::uuid",
                java.util.UUID.class, auction)).isNull();

        // EN: The store is back; the next tick closes it properly, payment and all.
        // VI: Kho dữ liệu hoạt động lại; nhịp sau đóng phiên đầy đủ, kèm cả khoản thanh toán.
        doCallRealMethod().when(payments).openFor(any(), any(), any(), any());
        assertThat(auctions.endDueAuctions(Instant.now(), 200)).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?::uuid", String.class, auction))
                .isEqualTo("ENDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE auction_id = ?::uuid",
                Integer.class, auction)).isEqualTo(1);
    }
}
