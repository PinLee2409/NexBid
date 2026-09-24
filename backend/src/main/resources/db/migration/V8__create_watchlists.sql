-- EN: Watchlists (guide §28, spec §15). A preference, not a record of fact — unlike a bid it can be
--     removed, and it goes away with the account or the lot it points at.
-- VI: Danh sách theo dõi (guide §28, spec §15). Là một sở thích, không phải bằng chứng — khác với lượt
--     trả giá, nó xoá được, và mất theo tài khoản hoặc lô mà nó trỏ tới.

CREATE TABLE watchlists (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    auction_id UUID        NOT NULL REFERENCES auctions (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,

    -- EN: Spec §23. Also serves "my watchlist", which reads by user.
    -- VI: Spec §23. Đồng thời phục vụ "danh sách của tôi", vốn đọc theo user.
    CONSTRAINT ux_watchlists_user_auction UNIQUE (user_id, auction_id)
);

-- EN: Deleting a draft auction cascades here; without an index on the column that would scan the table.
-- VI: Xoá một phiên nháp sẽ lan xuống bảng này; thiếu index trên cột này thì phải quét cả bảng.
CREATE INDEX ix_watchlists_auction ON watchlists (auction_id);
