-- EN: Auto bids (guide §31, spec §14, columns from spec §23). A standing instruction — "bid for me up to
--     this much" — so it can be switched off and on again, and it goes with the account or the lot.
-- VI: Trả giá tự động (guide §31, spec §14, các cột theo spec §23). Một chỉ thị thường trực — "trả giá
--     giúp tôi tới mức này" — nên tắt bật lại được, và mất theo tài khoản hoặc lô.

CREATE TABLE auto_bids (
    id         UUID          PRIMARY KEY,
    auction_id UUID          NOT NULL REFERENCES auctions (id) ON DELETE CASCADE,
    user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    max_amount NUMERIC(15,2) NOT NULL CHECK (max_amount > 0),
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,

    -- EN: Spec §23: one row per person per lot; switching off keeps the row, switching on reuses it.
    -- VI: Spec §23: mỗi người một dòng trên mỗi lô; tắt thì giữ dòng, bật lại thì dùng lại dòng đó.
    CONSTRAINT ux_auto_bids_auction_user UNIQUE (auction_id, user_id)
);

CREATE INDEX ix_auto_bids_user ON auto_bids (user_id);
