-- EN: Auctions (guide §14, spec §23). Turning a product into a lot with a price, a clock and a winner.
-- VI: Phiên đấu giá (guide §14, spec §23). Biến sản phẩm thành lô hàng có giá, có đồng hồ và có người thắng.

CREATE TABLE auctions (
    id                          UUID           PRIMARY KEY,
    product_id                  UUID           NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    -- EN: Denormalised from the product so bid rules can check "is this your own lot" without a join.
    -- VI: Lặp lại từ sản phẩm để luật trả giá kiểm "lô này có phải của chính bạn" mà không cần join.
    seller_id                   UUID           NOT NULL REFERENCES users (id) ON DELETE RESTRICT,

    -- EN: NUMERIC, never a float. Money that drifts by a cent is money someone loses.
    -- VI: Dùng NUMERIC, tuyệt đối không dùng float. Tiền lệch một xu là tiền có người mất thật.
    starting_price              NUMERIC(15,2)  NOT NULL CHECK (starting_price > 0),
    current_price               NUMERIC(15,2)  NOT NULL,
    minimum_increment           NUMERIC(15,2)  NOT NULL CHECK (minimum_increment > 0),

    start_time                  TIMESTAMPTZ    NOT NULL,
    end_time                    TIMESTAMPTZ    NOT NULL,
    -- EN: The database refuses a backwards clock, whatever the application forgets to check.
    -- VI: Database từ chối đồng hồ chạy ngược, dù ứng dụng có quên kiểm hay không.
    CONSTRAINT ck_auctions_time_order CHECK (end_time > start_time),

    status                      VARCHAR(20)    NOT NULL,

    anti_sniping_enabled        BOOLEAN        NOT NULL DEFAULT FALSE,
    anti_sniping_window_seconds INTEGER        NOT NULL DEFAULT 30,
    extension_seconds           INTEGER        NOT NULL DEFAULT 120,
    extension_count             INTEGER        NOT NULL DEFAULT 0,

    bid_count                   INTEGER        NOT NULL DEFAULT 0,
    winner_id                   UUID           REFERENCES users (id) ON DELETE SET NULL,
    rejection_reason            VARCHAR(500),

    -- EN: Optimistic locking (spec §9). Two bids landing together will not both win.
    -- VI: Khoá lạc quan (spec §9). Hai lượt trả giá vào cùng lúc sẽ không cùng thắng.
    version                     BIGINT         NOT NULL DEFAULT 0,

    created_at                  TIMESTAMPTZ    NOT NULL,
    updated_at                  TIMESTAMPTZ    NOT NULL
);

-- EN: A product can only be in one live auction at a time. Partial index, so a product may be
--     re-listed after a previous attempt was rejected or cancelled.
-- VI: Một sản phẩm chỉ được nằm trong một phiên còn sống tại một thời điểm. Index có điều kiện, nên
--     sản phẩm vẫn đăng lại được sau khi lần trước bị từ chối hoặc huỷ.
CREATE UNIQUE INDEX ux_auctions_one_live_per_product
    ON auctions (product_id)
    WHERE status IN ('DRAFT', 'PENDING_APPROVAL', 'SCHEDULED', 'ACTIVE', 'ENDED');

-- EN: The browse page reads "what is live, closing soonest".
-- VI: Trang duyệt đọc theo kiểu "cái nào đang chạy, sắp đóng nhất".
CREATE INDEX ix_auctions_status_end ON auctions (status, end_time);

-- EN: The seller's own list.
-- VI: Danh sách của chính người bán.
CREATE INDEX ix_auctions_seller ON auctions (seller_id, created_at DESC);
