-- EN: Products (guide §11, spec §23). A seller owns each one; auctions will point at them from function 12.
-- VI: Sản phẩm (guide §11, spec §23). Mỗi sản phẩm thuộc một người bán; auction sẽ trỏ tới từ chức năng 12.

CREATE TABLE products (
    id          UUID         PRIMARY KEY,
    -- EN: RESTRICT, not CASCADE: deleting a seller must not silently erase bid history attached to their lots.
    -- VI: RESTRICT chứ không CASCADE: xoá người bán không được âm thầm xoá luôn lịch sử đấu giá của lô hàng họ.
    seller_id   UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    category_id UUID         NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    name        VARCHAR(160) NOT NULL,
    description TEXT         NOT NULL,
    condition   VARCHAR(16)  NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

-- EN: Every seller screen reads "my products, newest first"; without this it scans the whole table.
-- VI: Mọi màn của người bán đều đọc "sản phẩm của tôi, mới nhất trước"; thiếu index này sẽ quét cả bảng.
CREATE INDEX ix_products_seller_created ON products (seller_id, created_at DESC);

-- EN: Browsing by category is the other common read.
-- VI: Duyệt theo danh mục là kiểu đọc phổ biến còn lại.
CREATE INDEX ix_products_category ON products (category_id);
