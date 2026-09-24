package com.nexbid.auction;

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
 * EN: The V7 backfill, run against data shaped like it was before V7 existed. A migration runs once, on
 *     real rows, and cannot be taken back — so every branch of it is exercised here, not just the schema.
 * VI: Phần bù dữ liệu của V7, chạy trên dữ liệu có hình dạng như trước khi có V7. Migration chỉ chạy một
 *     lần, trên dữ liệu thật, và không rút lại được — nên mọi nhánh của nó đều được chạy thử ở đây.
 */
class WinnerMigrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    private static JdbcTemplate jdbc;
    private static DriverManagerDataSource dataSource;

    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    /** EN: Closed before winners existed, with bids. / VI: Đóng trước khi có người thắng, có lượt trả giá. */
    private static final UUID ENDED_WITH_BIDS = UUID.randomUUID();
    /** EN: Closed before winners existed, unsold. / VI: Đóng trước khi có người thắng, không bán được. */
    private static final UUID ENDED_UNSOLD = UUID.randomUUID();
    /** EN: Still running, with bids. / VI: Vẫn đang chạy, có lượt trả giá. */
    private static final UUID RUNNING = UUID.randomUUID();
    /** EN: Rejected, never held its product. / VI: Bị từ chối, chưa từng giữ sản phẩm. */
    private static final UUID REJECTED = UUID.randomUUID();

    private static UUID productOfEndedWithBids;
    private static UUID productOfEndedUnsold;
    private static UUID productOfRunning;

    @BeforeAll
    static void migrateToV6SeedThenMigrateToV7() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        flywayUpTo("6");

        for (UUID user : new UUID[] { SELLER, ALICE, BOB }) {
            jdbc.update("""
                    INSERT INTO users (id, full_name, email, password, status, created_at, updated_at)
                    VALUES (?, 'Someone', ?, 'x', 'ACTIVE', now(), now())
                    """, user, user + "@nexbid.com");
        }

        productOfEndedWithBids = product("IN_AUCTION");
        productOfEndedUnsold = product("IN_AUCTION");
        productOfRunning = product("IN_AUCTION");
        UUID productOfRejected = product("AVAILABLE");

        auction(ENDED_WITH_BIDS, productOfEndedWithBids, "ENDED", "12000000", 4);
        auction(ENDED_UNSOLD, productOfEndedUnsold, "ENDED", "10000000", 0);
        auction(RUNNING, productOfRunning, "ACTIVE", "10500000", 2);
        auction(REJECTED, productOfRejected, "REJECTED", "10000000", 0);

        // EN: Bob opens, the two trade places, Alice ends on top — so "first bidder" and "top bidder" differ,
        //     and a backfill that picked the wrong one would be caught.
        // VI: Bob mở màn, hai người thay nhau dẫn, Alice kết thúc ở trên cùng — nên "người trả đầu tiên" và
        //     "người trả cao nhất" khác nhau, và phần bù nào chọn nhầm sẽ bị bắt.
        bid(ENDED_WITH_BIDS, BOB, "10000000", 4);
        bid(ENDED_WITH_BIDS, ALICE, "11000000", 3);
        bid(ENDED_WITH_BIDS, BOB, "11500000", 2);
        bid(ENDED_WITH_BIDS, ALICE, "12000000", 1);

        bid(RUNNING, ALICE, "10000000", 2);
        bid(RUNNING, BOB, "10500000", 1);

        flywayUpTo("7");
    }

    @AfterAll
    static void stop() {
        POSTGRES.stop();
    }

    private static void flywayUpTo(String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private static UUID product(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO products (id, seller_id, category_id, name, description, condition, status,
                                      created_at, updated_at)
                SELECT ?, ?, (SELECT id FROM categories LIMIT 1), 'Item', 'A description.', 'NEW', ?, now(), now()
                """, id, SELLER, status);
        return id;
    }

    private static void auction(UUID id, UUID product, String status, String price, int bidCount) {
        jdbc.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, current_price, minimum_increment,
                                      start_time, end_time, status, bid_count, created_at, updated_at)
                VALUES (?, ?, ?, 10000000, ?::numeric, 500000,
                        now() - interval '3 hours', now() - interval '1 hour', ?, ?, now(), now())
                """, id, product, SELLER, price, status, bidCount);
    }

    private static void bid(UUID auction, UUID bidder, String amount, int minutesAgo) {
        jdbc.update("""
                INSERT INTO bids (id, auction_id, bidder_id, amount, created_at)
                VALUES (gen_random_uuid(), ?, ?, ?::numeric, now() - make_interval(mins => ?))
                """, auction, bidder, amount, minutesAgo);
    }

    private static UUID column(String column, UUID auction) {
        return jdbc.queryForObject("SELECT " + column + " FROM auctions WHERE id = ?", UUID.class, auction);
    }

    private static String productStatus(UUID product) {
        return jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, product);
    }

    @Test
    void everyLotWithBidsLearnsWhoLeads() {
        assertThat(column("leading_bidder_id", ENDED_WITH_BIDS)).isEqualTo(ALICE);
        assertThat(column("leading_bidder_id", RUNNING)).isEqualTo(BOB);
        assertThat(column("leading_bidder_id", ENDED_UNSOLD)).isNull();
    }

    @Test
    void aLotClosedBeforeWinnersExistedGetsItsTopBidderNotItsFirst() {
        assertThat(column("winner_id", ENDED_WITH_BIDS)).isEqualTo(ALICE);
        assertThat(productStatus(productOfEndedWithBids)).isEqualTo("IN_AUCTION");
    }

    @Test
    void aRunningLotGetsALeaderButNoWinner() {
        assertThat(column("winner_id", RUNNING)).isNull();
        assertThat(productStatus(productOfRunning)).isEqualTo("IN_AUCTION");
    }

    @Test
    void anUnsoldClosedLotReleasesItsProduct() {
        assertThat(column("winner_id", ENDED_UNSOLD)).isNull();
        assertThat(productStatus(productOfEndedUnsold)).isEqualTo("AVAILABLE");

        // EN: And the rewritten index agrees: the product can be listed again.
        // VI: Và index viết lại cũng đồng ý: sản phẩm đăng lại được.
        jdbc.update("""
                INSERT INTO auctions (id, product_id, seller_id, starting_price, current_price, minimum_increment,
                                      start_time, end_time, status, created_at, updated_at)
                VALUES (gen_random_uuid(), ?, ?, 1, 1, 1, now() + interval '1 hour', now() + interval '2 hours',
                        'DRAFT', now(), now())
                """, productOfEndedUnsold, SELLER);
    }

    @Test
    void aWonLotStillHoldsItsProductAtTheDatabaseLevel() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("""
                        INSERT INTO auctions (id, product_id, seller_id, starting_price, current_price,
                                              minimum_increment, start_time, end_time, status, created_at, updated_at)
                        VALUES (gen_random_uuid(), ?, ?, 1, 1, 1, now() + interval '1 hour',
                                now() + interval '2 hours', 'DRAFT', now(), now())
                        """, productOfEndedWithBids, SELLER))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }
}
