-- EN: A bid taken back. The bid row itself is deleted, so every count, history and winner query stays as it is;
--     this table is the record of what was there and why it went.
-- VI: Một lượt trả giá bị rút lại. Bản thân dòng bid bị xoá, nên mọi truy vấn đếm, lịch sử và người thắng giữ nguyên;
--     bảng này là bản ghi về thứ đã có ở đó và lý do nó bị rút.
CREATE TABLE bid_retractions (
    id              UUID PRIMARY KEY,
    auction_id      UUID          NOT NULL REFERENCES auctions (id),
    bidder_id       UUID          NOT NULL REFERENCES users (id),
    -- EN: The highest of the bids taken back, and how many there were. / VI: Lượt cao nhất trong các lượt bị rút, và số lượt.
    amount          NUMERIC(15, 2) NOT NULL,
    bids_removed    INTEGER       NOT NULL,
    bid_placed_at   TIMESTAMPTZ   NOT NULL,
    reason          VARCHAR(20)   NOT NULL CHECK (reason IN ('QUICK', 'TYPO')),
    note            VARCHAR(300),
    price_before    NUMERIC(15, 2) NOT NULL,
    price_after     NUMERIC(15, 2) NOT NULL,
    was_leading     BOOLEAN       NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_bid_retractions_bidder ON bid_retractions (bidder_id, created_at DESC);
CREATE INDEX ix_bid_retractions_created ON bid_retractions (created_at DESC);

-- EN: Reference rates, one row per currency: how many đồng one unit buys. Display only; nothing is charged in them.
-- VI: Tỷ giá tham khảo, mỗi loại tiền một dòng: một đơn vị đổi được bao nhiêu đồng. Chỉ để hiển thị; không thu tiền theo đó.
CREATE TABLE exchange_rates (
    currency    VARCHAR(3)     PRIMARY KEY,
    vnd_per     NUMERIC(15, 4) NOT NULL CHECK (vnd_per > 0),
    source      VARCHAR(40)    NOT NULL,
    fetched_at  TIMESTAMPTZ    NOT NULL
);
