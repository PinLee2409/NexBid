-- EN: Second-chance offers (spec §17, "Version nâng cao có thể offer cho người trả giá cao thứ hai"). When a winner
--     lets the payment lapse, the seller may offer the lot once to the next-highest bidder, at that bidder's own
--     highest bid, for 24 hours. Accepting gives the lot a second payment and order, so a lot may now have more
--     than one of each — but never two alive at once.
-- VI: Đề nghị cơ hội thứ hai (spec §17, "Version nâng cao có thể offer cho người trả giá cao thứ hai"). Khi người
--     thắng để quá hạn thanh toán, người bán được đề nghị lô một lần cho người trả giá cao thứ hai, với giá cao nhất
--     của chính người đó, trong 24 giờ. Nhận thì lô có khoản thanh toán và đơn hàng thứ hai, nên một lô giờ có thể có
--     nhiều hơn một của mỗi loại — nhưng không bao giờ có hai cái cùng còn hiệu lực.
ALTER TABLE payments DROP CONSTRAINT ux_payments_auction;
CREATE UNIQUE INDEX ux_payments_auction_live ON payments (auction_id) WHERE status <> 'EXPIRED';
CREATE INDEX ix_payments_auction ON payments (auction_id);

ALTER TABLE orders DROP CONSTRAINT ux_orders_auction;
CREATE UNIQUE INDEX ux_orders_auction_live ON orders (auction_id) WHERE status <> 'CANCELLED';
CREATE INDEX ix_orders_auction ON orders (auction_id);

CREATE TABLE second_chance_offers (
    id          UUID          PRIMARY KEY,
    auction_id  UUID          NOT NULL REFERENCES auctions (id) ON DELETE CASCADE,
    seller_id   UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    buyer_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount      NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    status      VARCHAR(16)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    expires_at  TIMESTAMPTZ   NOT NULL,
    decided_at  TIMESTAMPTZ,
    CONSTRAINT ck_second_chance_offers_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),
    -- EN: Once per lot, whatever came of it. / VI: Mỗi lô một lần, bất kể kết quả ra sao.
    CONSTRAINT ux_second_chance_offers_auction UNIQUE (auction_id)
);

CREATE INDEX ix_second_chance_offers_seller ON second_chance_offers (seller_id);
CREATE INDEX ix_second_chance_offers_buyer ON second_chance_offers (buyer_id, created_at DESC);
CREATE INDEX ix_second_chance_offers_open ON second_chance_offers (expires_at) WHERE status = 'PENDING';

-- EN: A seller can now lose a sale twice on one lot (the winner, then the runner-up), so a cancelled sale is no
--     longer once per person per lot. Kafka redeliveries are still stopped by consumed_events.
-- VI: Người bán giờ có thể mất giao dịch hai lần trên một lô (người thắng, rồi người thứ hai), nên giao dịch bị huỷ
--     không còn là một-lần-mỗi-người-mỗi-lô. Kafka giao lại vẫn bị consumed_events chặn.
DROP INDEX ux_notifications_once_per_lot;
CREATE UNIQUE INDEX ux_notifications_once_per_lot
    ON notifications (user_id, auction_id, type)
    WHERE type IN ('AUCTION_STARTING', 'AUCTION_ENDING', 'AUCTION_WON', 'AUCTION_LOST', 'PAYMENT_REQUIRED');
