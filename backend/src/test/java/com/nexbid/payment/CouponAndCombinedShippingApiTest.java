package com.nexbid.payment;

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
import org.springframework.test.web.servlet.ResultActions;

import com.nexbid.auction.AuctionService;
import com.nexbid.support.Market;
import com.nexbid.support.Market.Account;
import com.nexbid.support.TestInfrastructure;
import com.nexbid.support.VnpayTestSupport;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: A seller's own code comes off the goods and out of the seller's earnings; a second win from the same seller,
 *     paid after the first, rides in the first one's parcel for free.
 * VI: Mã riêng của người bán trừ vào tiền hàng và vào thu nhập của người bán; lô thắng thứ hai của cùng người bán, trả
 *     sau lô đầu, đi chung kiện của lô đầu và được miễn phí ship.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class CouponAndCombinedShippingApiTest {

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
    private Account buyer;

    @BeforeEach
    void setUp() throws Exception {
        market = new Market(mockMvc, users, auctions, jdbc, json);
        seller = market.account("coupon.seller", RoleName.SELLER);
        admin = market.account("coupon.admin", RoleName.ADMIN);
        buyer = market.account("coupon.buyer", RoleName.BUYER);
        VnpayTestSupport.saveAddress(mockMvc, buyer.token());
    }

    private String wonPayment(String name) throws Exception {
        String lot = market.openLot(seller, admin, name, 10_000_000, null);
        market.bid(buyer, lot, 10_000_000).andExpect(status().isCreated());
        market.close(lot);
        return jdbc.queryForObject("SELECT id::text FROM payments WHERE auction_id = ?::uuid", String.class, lot);
    }

    private ResultActions coupon(Account who, String paymentId, String code) throws Exception {
        return market.as(who, post("/api/payments/" + paymentId + "/coupon").contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"));
    }

    @Test
    void aSellerCodeComesOffTheGoodsAndTheSellersEarnings() throws Exception {
        String code = "SAVE10-" + Long.toString(System.nanoTime(), 36).toUpperCase();
        market.as(seller, post("/api/seller/coupons").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"%s","percentOff":10,"maxUses":1,"followersOnly":true}
                """.formatted(code))).andExpect(jsonPath("$.data.code").value(code));
        market.as(seller, post("/api/seller/coupons").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"BAD-%s","percentOff":60,"maxUses":1}
                """.formatted(code))).andExpect(jsonPath("$.code").value("COUPON_INVALID"));

        String paymentId = wonPayment("Coupon lot");
        coupon(buyer, paymentId, code).andExpect(jsonPath("$.code").value("COUPON_INVALID"));
        // EN: Following the seller opens the code. / VI: Theo dõi người bán thì dùng được mã.
        market.as(buyer, put("/api/alerts/sellers/" + seller.id())).andExpect(status().isOk());
        coupon(buyer, paymentId, code)
                .andExpect(jsonPath("$.data.sellerDiscount").value(1000000))
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.closeTo(9000000.0 + 50000.0, 200000.0)));

        // EN: Another seller's code does not apply here. / VI: Mã của người bán khác không dùng được ở đây.
        Account other = market.account("coupon.other", RoleName.SELLER);
        market.as(other, post("/api/seller/coupons").contentType(MediaType.APPLICATION_JSON).content("""
                {"code":"OTHER-%s","amountOff":500000,"maxUses":5}
                """.formatted(code))).andExpect(status().isOk());
        coupon(buyer, paymentId, "OTHER-" + code).andExpect(jsonPath("$.code").value("COUPON_INVALID"));
        coupon(buyer, paymentId, code).andExpect(status().isOk());

        VnpayTestSupport.pay(mockMvc, buyer.token(), paymentId, "SUCCESS").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT used FROM seller_coupons WHERE code = ?", Integer.class, code)).isEqualTo(1);
        String orderId = jdbc.queryForObject("SELECT id::text FROM orders WHERE payment_id = ?::uuid", String.class,
                paymentId);
        assertThat(market.column("orders", "seller_discount", orderId)).isEqualTo("1000000.00");

        market.as(seller, post("/api/seller/orders/" + orderId + "/ship").contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"GHN\",\"trackingNumber\":\"CPN" + System.nanoTime() % 100000 + "\"}"))
                .andExpect(status().isOk());
        market.as(buyer, post("/api/users/me/orders/" + orderId + "/received")).andExpect(status().isOk());
        // EN: The seller sold for 9m; the fee is on that. / VI: Người bán bán được 9 triệu; phí sàn tính trên số đó.
        assertThat(jdbc.queryForObject("SELECT gross::text FROM seller_earnings WHERE order_id = ?::uuid", String.class,
                orderId)).isEqualTo("9000000.00");

        // EN: One use only. / VI: Chỉ một lượt.
        String second = wonPayment("Coupon lot two");
        coupon(buyer, second, code).andExpect(jsonPath("$.code").value("COUPON_INVALID"));
    }

    @Test
    void aSecondWinFromTheSameSellerShipsFreeInTheFirstParcel() throws Exception {
        String first = wonPayment("Combined first");
        String second = wonPayment("Combined second");
        VnpayTestSupport.pay(mockMvc, buyer.token(), first, "SUCCESS").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT shipping_fee FROM payments WHERE id = ?::uuid", java.math.BigDecimal.class,
                first).signum()).isPositive();

        market.as(buyer, post("/api/payments/" + second + "/shipping-quote"))
                .andExpect(jsonPath("$.data.source").value("COMBINED"))
                .andExpect(jsonPath("$.data.shippingFee").value(0))
                .andExpect(jsonPath("$.data.combinedWithAuctionId").isNotEmpty());
        VnpayTestSupport.pay(mockMvc, buyer.token(), second, "SUCCESS").andExpect(status().isOk());
        market.as(seller, get("/api/seller/orders"))
                .andExpect(jsonPath("$.data[?(@.payment.id == '" + second + "')].payment.shipsWithAuctionId")
                        .isNotEmpty());

        // EN: Once the first parcel has left, a new win pays its own shipping. / VI: Khi kiện đầu đã đi, lô thắng mới trả phí ship riêng.
        String orderId = jdbc.queryForObject("SELECT id::text FROM orders WHERE payment_id = ?::uuid", String.class, first);
        market.as(seller, post("/api/seller/orders/" + orderId + "/ship").contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"GHN\",\"trackingNumber\":\"CMB" + System.nanoTime() % 100000 + "\"}"))
                .andExpect(status().isOk());
        String secondOrder = jdbc.queryForObject("SELECT id::text FROM orders WHERE payment_id = ?::uuid", String.class,
                second);
        market.as(seller, post("/api/seller/orders/" + secondOrder + "/ship").contentType(MediaType.APPLICATION_JSON)
                .content("{\"carrier\":\"GHN\",\"trackingNumber\":\"CMC" + System.nanoTime() % 100000 + "\"}"))
                .andExpect(status().isOk());
        String third = wonPayment("Combined third");
        market.as(buyer, post("/api/payments/" + third + "/shipping-quote"))
                .andExpect(jsonPath("$.data.source").value(org.hamcrest.Matchers.not("COMBINED")));
    }
}
