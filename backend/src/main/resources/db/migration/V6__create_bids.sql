-- EN: Bids (guide §20, spec §23). An append-only record: a bid placed is never edited or removed.
-- VI: Lượt trả giá (guide §20, spec §23). Chỉ ghi thêm: lượt đã đặt không bao giờ được sửa hay xoá.

CREATE TABLE bids (
    id         UUID          PRIMARY KEY,
    -- EN: RESTRICT, so an auction with history cannot be deleted out from under it.
    -- VI: RESTRICT, để phiên đã có lịch sử không bị xoá mất từ bên dưới.
    auction_id UUID          NOT NULL REFERENCES auctions (id) ON DELETE RESTRICT,
    bidder_id  UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    amount     NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ   NOT NULL
);

-- EN: Bid history always reads "this lot's, newest first".
-- VI: Lịch sử trả giá luôn đọc theo kiểu "của lô này, mới nhất trước".
CREATE INDEX ix_bids_auction_created ON bids (auction_id, created_at DESC);

-- EN: "My bids" on the buyer's account page.
-- VI: Mục "lượt trả giá của tôi" trên trang tài khoản người mua.
CREATE INDEX ix_bids_bidder ON bids (bidder_id, created_at DESC);

-- EN: Two bids cannot claim the same amount on the same lot. The price only ever rises, so an equal
--     amount means one of them was computed from a stale price — the thing function 19 is about.
-- VI: Hai lượt không thể cùng một số tiền trên cùng một lô. Giá chỉ tăng, nên hai số bằng nhau nghĩa là
--     một trong hai được tính từ mức giá đã cũ — đúng thứ mà chức năng 19 nói tới.
CREATE UNIQUE INDEX ux_bids_auction_amount ON bids (auction_id, amount);
