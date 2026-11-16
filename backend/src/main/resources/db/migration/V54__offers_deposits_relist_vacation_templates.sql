-- EN: Offers on buy-now lots: a buyer names a price below the buy-now price, the seller accepts, declines or counters.
--     An open offer is PENDING (waiting for the seller) or COUNTERED (waiting for the buyer); it lapses at expires_at.
-- VI: Trả giá đề nghị cho lô có giá mua ngay: người mua đưa ra một giá thấp hơn giá mua ngay, người bán nhận, từ chối hoặc
--     trả giá lại. Đề nghị còn mở là PENDING (chờ người bán) hoặc COUNTERED (chờ người mua); hết hạn ở expires_at.
CREATE TABLE lot_offers (
    id             UUID          PRIMARY KEY,
    auction_id     UUID          NOT NULL REFERENCES auctions (id) ON DELETE CASCADE,
    buyer_id       UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    amount         NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    counter_amount NUMERIC(15,2) CHECK (counter_amount > 0),
    status         VARCHAR(12)   NOT NULL
        CONSTRAINT ck_lot_offers_status CHECK (status IN ('PENDING', 'COUNTERED', 'ACCEPTED', 'DECLINED', 'WITHDRAWN')),
    message        VARCHAR(300),
    expires_at     TIMESTAMPTZ   NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    answered_at    TIMESTAMPTZ
);
CREATE INDEX ix_lot_offers_auction ON lot_offers (auction_id, created_at DESC);
CREATE INDEX ix_lot_offers_buyer ON lot_offers (buyer_id, created_at DESC);
-- EN: One open offer per buyer per lot. / VI: Mỗi người mua chỉ có một đề nghị còn mở trên mỗi lô.
CREATE UNIQUE INDEX ux_lot_offers_open ON lot_offers (auction_id, buyer_id) WHERE status IN ('PENDING', 'COUNTERED');

-- EN: Deposits for expensive lots, paid through VNPay before bidding. One row per checkout attempt; at most one PAID
--     (still held) row per person per lot. Held money is returned by an admin transfer (REFUND_DUE, then REFUNDED), or
--     kept when the winner never pays (FORFEITED).
-- VI: Tiền cọc cho lô đắt, trả qua VNPay trước khi trả giá. Mỗi lần checkout một dòng; mỗi người mỗi lô có tối đa một
--     dòng PAID (đang giữ). Tiền đang giữ được admin chuyển trả (REFUND_DUE, rồi REFUNDED), hoặc bị giữ lại khi người
--     thắng không bao giờ trả tiền (FORFEITED).
CREATE TABLE lot_deposits (
    id             UUID          PRIMARY KEY,
    auction_id     UUID          NOT NULL REFERENCES auctions (id) ON DELETE RESTRICT,
    user_id        UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    amount         NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    txn_ref        VARCHAR(40)   NOT NULL UNIQUE,
    status         VARCHAR(12)   NOT NULL
        CONSTRAINT ck_lot_deposits_status
            CHECK (status IN ('PENDING', 'FAILED', 'PAID', 'REFUND_DUE', 'REFUNDED', 'FORFEITED')),
    response_code  VARCHAR(8),
    transaction_no VARCHAR(40),
    reference      VARCHAR(80),
    account        VARCHAR(120),
    settled_by     UUID,
    created_at     TIMESTAMPTZ   NOT NULL,
    paid_at        TIMESTAMPTZ,
    settled_at     TIMESTAMPTZ
);
CREATE INDEX ix_lot_deposits_user ON lot_deposits (user_id, created_at DESC);
CREATE INDEX ix_lot_deposits_due ON lot_deposits (status, paid_at) WHERE status = 'REFUND_DUE';
CREATE UNIQUE INDEX ux_lot_deposits_held ON lot_deposits (auction_id, user_id) WHERE status = 'PAID';

-- EN: Automatic relisting of a lot that closed unsold: how many more times, the price drop each time, and where a
--     relisted lot came from.
-- VI: Tự đăng lại lô đóng mà không bán được: còn bao nhiêu lần, mỗi lần hạ giá bao nhiêu, và lô đăng lại xuất phát từ đâu.
ALTER TABLE auctions
    ADD COLUMN relist_left INTEGER NOT NULL DEFAULT 0 CONSTRAINT ck_auctions_relist_left CHECK (relist_left BETWEEN 0 AND 5),
    ADD COLUMN relist_drop_percent INTEGER NOT NULL DEFAULT 0
        CONSTRAINT ck_auctions_relist_drop CHECK (relist_drop_percent BETWEEN 0 AND 50),
    ADD COLUMN relisted_from UUID REFERENCES auctions (id) ON DELETE SET NULL;

-- EN: A seller away: until when, and a short note for buyers. / VI: Người bán đi vắng: tới khi nào, và lời nhắn ngắn cho người mua.
ALTER TABLE users
    ADD COLUMN vacation_until TIMESTAMPTZ,
    ADD COLUMN vacation_note VARCHAR(200);

-- EN: A seller's reusable listing text, category, condition and parcel size.
-- VI: Mô tả, danh mục, tình trạng và kích thước kiện hàng dùng lại được của người bán.
CREATE TABLE listing_templates (
    id          UUID         PRIMARY KEY,
    seller_id   UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(80)  NOT NULL,
    description TEXT         NOT NULL,
    category_id UUID         REFERENCES categories (id) ON DELETE SET NULL,
    condition   VARCHAR(20),
    weight_g    INTEGER,
    length_cm   INTEGER,
    width_cm    INTEGER,
    height_cm   INTEGER,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_listing_templates_name UNIQUE (seller_id, name)
);
