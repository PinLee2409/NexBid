-- EN: Lot alerts. A buyer saves a catalogue search or follows a seller; each newly approved lot that fits is recorded
--     once per person (with an in-app notice), and a daily email lists the day's matches.
-- VI: Báo lô mới. Người mua lưu một bộ lọc danh mục hoặc theo dõi một người bán; mỗi lô vừa được duyệt mà khớp được ghi
--     một lần cho mỗi người (kèm thông báo trong app), và email mỗi ngày liệt kê các lô khớp trong ngày.
CREATE TABLE saved_searches (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- EN: The catalogue's own filters; empty or null means "any". / VI: Chính các bộ lọc của danh mục; rỗng hoặc null là "bất kỳ".
    q           VARCHAR(100),
    categories  TEXT[]        NOT NULL DEFAULT '{}',
    conditions  TEXT[]        NOT NULL DEFAULT '{}',
    min_price   NUMERIC(15,2),
    max_price   NUMERIC(15,2),
    created_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_saved_searches_user ON saved_searches (user_id, created_at DESC);

CREATE TABLE seller_follows (
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    seller_id   UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ   NOT NULL,
    PRIMARY KEY (user_id, seller_id),
    CHECK (user_id <> seller_id)
);
CREATE INDEX ix_seller_follows_seller ON seller_follows (seller_id);

-- EN: One row per person per lot, whichever search or follow found it first; emailed_at marks it as in a digest.
-- VI: Mỗi người mỗi lô một dòng, do bộ lọc hay lượt theo dõi nào tìm thấy trước; emailed_at đánh dấu đã vào email tổng hợp.
CREATE TABLE lot_alerts (
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    auction_id  UUID          NOT NULL REFERENCES auctions (id) ON DELETE CASCADE,
    search_id   UUID          REFERENCES saved_searches (id) ON DELETE SET NULL,
    seller_id   UUID,
    created_at  TIMESTAMPTZ   NOT NULL,
    emailed_at  TIMESTAMPTZ,
    PRIMARY KEY (user_id, auction_id)
);
CREATE INDEX ix_lot_alerts_unsent ON lot_alerts (user_id) WHERE emailed_at IS NULL;

ALTER TABLE users ADD COLUMN digest_emails BOOLEAN NOT NULL DEFAULT TRUE;
