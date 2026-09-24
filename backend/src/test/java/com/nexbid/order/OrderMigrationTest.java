package com.nexbid.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * EN: The V12 backfill against payments in every state they can be in before orders existed. It runs once,
 *     on real rows, so every branch is exercised here.
 * VI: Phần bù dữ liệu của V12 trên các khoản thanh toán ở mọi trạng thái có thể có trước khi có đơn hàng. Nó
 *     chỉ chạy một lần, trên dữ liệu thật, nên mọi nhánh đều được chạy thử ở đây.
 */
class OrderMigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID BUYER = UUID.randomUUID();

    /** EN: One lot per payment state: auction id, product id. / VI: Mỗi trạng thái thanh toán một lô: id phiên, id sản phẩm. */
    private static final UUID[] PENDING = { UUID.randomUUID(), UUID.randomUUID() };
    private static final UUID[] PAID = { UUID.randomUUID(), UUID.randomUUID() };
    private static final UUID[] FAILED = { UUID.randomUUID(), UUID.randomUUID() };
    private static final UUID[] EXPIRED = { UUID.randomUUID(), UUID.randomUUID() };

    @BeforeAll
    static void migrateToV11SeedThenMigrateToV12() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        flywayUpTo("11");

        for (UUID user : new UUID[] { SELLER, BUYER }) {
            jdbc.update("INSERT INTO users (id, full_name, email, password, status, created_at, updated_at) "
                    + "VALUES (?, 'Someone', ?, 'x', 'ACTIVE', now(), now())", user, user + "@nexbid.com");
        }

        lot(PENDING, "ENDED", "IN_AUCTION", "PENDING");
        lot(PAID, "ENDED", "IN_AUCTION", "SUCCESS");
        lot(FAILED, "ENDED", "IN_AUCTION", "FAILED");
        // EN: An expired payment has already cancelled its lot and freed its product (function 30).
        // VI: Khoản đã hết hạn thì đã huỷ lô và trả sản phẩm từ trước (chức năng 30).
        lot(EXPIRED, "CANCELLED", "AVAILABLE", "EXPIRED");

        flywayUpTo("12");
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    private static void flywayUpTo(String version) {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(version).load().migrate();
    }

    private static void lot(UUID[] ids, String auctionStatus, String productStatus, String paymentStatus) {
        jdbc.update("""
                INSERT INTO products (id, seller_id, category_id, name, description, condition, status, created_at, updated_at)
                SELECT ?, ?, (SELECT id FROM categories LIMIT 1), 'Item', 'A description.', 'NEW', ?, now(), now()
                """, ids[1], SELLER, productStatus);
        jdbc.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, current_price, minimum_increment,
                                      start_time, end_time, status, bid_count, winner_id, leading_bidder_id,
                                      created_at, updated_at)
                VALUES (?, ?, ?, 10000000, 12000000, 500000, now() - interval '3 hours', now() - interval '1 hour',
                        ?, 1, ?, ?, now(), now())
                """, ids[0], ids[1], SELLER, auctionStatus, BUYER, BUYER);
        jdbc.update("""
                INSERT INTO payments (id, auction_id, user_id, amount, status, expired_at, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, 12000000, ?, now() + interval '1 day', now(), now())
                """, ids[0], BUYER, paymentStatus);
    }

    private static String order(UUID[] ids) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE auction_id = ?", String.class, ids[0]);
    }

    private static String auction(UUID[] ids) {
        return jdbc.queryForObject("SELECT status FROM auctions WHERE id = ?", String.class, ids[0]);
    }

    private static String product(UUID[] ids) {
        return jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, ids[1]);
    }

    @Test
    void everyPaymentGetsOneOrderInTheMatchingState() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(4);

        assertThat(order(PENDING)).isEqualTo("PENDING_PAYMENT");
        assertThat(order(FAILED)).isEqualTo("PENDING_PAYMENT");
        assertThat(order(PAID)).isEqualTo("PAID");
        assertThat(order(EXPIRED)).isEqualTo("CANCELLED");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE buyer_id = ? AND seller_id = ?",
                Integer.class, BUYER, SELLER)).isEqualTo(4);
    }

    @Test
    void aSalePaidBeforeOrdersExistedIsCompletedNow() {
        assertThat(auction(PAID)).isEqualTo("COMPLETED");
        assertThat(product(PAID)).isEqualTo("SOLD");
    }

    @Test
    void nothingElseIsTouched() {
        assertThat(auction(PENDING)).isEqualTo("ENDED");
        assertThat(product(PENDING)).isEqualTo("IN_AUCTION");
        assertThat(auction(FAILED)).isEqualTo("ENDED");
        assertThat(product(FAILED)).isEqualTo("IN_AUCTION");
        assertThat(auction(EXPIRED)).isEqualTo("CANCELLED");
        assertThat(product(EXPIRED)).isEqualTo("AVAILABLE");
    }
}
