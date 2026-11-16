-- EN: A seller's own discount codes, off the goods price of their lots. The seller bears the discount: it comes off
--     what they are owed. Either a percentage or a fixed amount; optionally only for people who follow the seller.
-- VI: Mã giảm giá riêng của người bán, trừ vào giá hàng của các lô của họ. Người bán chịu khoản giảm: nó bị trừ vào số
--     tiền họ được nhận. Hoặc phần trăm hoặc số tiền cố định; có thể chỉ dành cho người theo dõi người bán.
CREATE TABLE seller_coupons (
    id             UUID          PRIMARY KEY,
    seller_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code           VARCHAR(32)   NOT NULL,
    percent_off    INTEGER       CONSTRAINT ck_seller_coupons_percent CHECK (percent_off BETWEEN 1 AND 50),
    amount_off     NUMERIC(15,2) CONSTRAINT ck_seller_coupons_amount CHECK (amount_off > 0),
    min_price      NUMERIC(15,2),
    followers_only BOOLEAN       NOT NULL DEFAULT FALSE,
    max_uses       INTEGER       NOT NULL CHECK (max_uses > 0),
    used           INTEGER       NOT NULL DEFAULT 0,
    expires_at     TIMESTAMPTZ,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_seller_coupons_kind CHECK ((percent_off IS NULL) <> (amount_off IS NULL)),
    CONSTRAINT ux_seller_coupons_code UNIQUE (code)
);
CREATE INDEX ix_seller_coupons_seller ON seller_coupons (seller_id, created_at DESC);

-- EN: What the seller's coupon took off this payment, and which payment's parcel this one ships in.
-- VI: Mã của người bán đã trừ bao nhiêu khỏi khoản này, và khoản này được gửi chung kiện với khoản nào.
ALTER TABLE payments
    ADD COLUMN seller_discount NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN seller_coupon VARCHAR(32),
    ADD COLUMN combined_with UUID REFERENCES payments (id) ON DELETE SET NULL;
ALTER TABLE orders ADD COLUMN seller_discount NUMERIC(15,2) NOT NULL DEFAULT 0;

-- EN: A sealed lot: one hidden bid per person, the highest wins at the close. / VI: Lô đấu giá kín: mỗi người một lượt giá ẩn, giá cao nhất thắng khi đóng.
ALTER TABLE auctions ADD COLUMN sealed BOOLEAN NOT NULL DEFAULT FALSE;

-- EN: Photos and videos an admin has looked at. What was there before this migration counts as looked at.
-- VI: Ảnh và video admin đã xem qua. Những gì có trước migration này coi như đã xem.
ALTER TABLE product_images ADD COLUMN reviewed_at TIMESTAMPTZ;
UPDATE product_images SET reviewed_at = now();
ALTER TABLE products ADD COLUMN video_reviewed_at TIMESTAMPTZ;
UPDATE products SET video_reviewed_at = now() WHERE video_url IS NOT NULL;
CREATE INDEX ix_product_images_unreviewed ON product_images (created_at DESC) WHERE reviewed_at IS NULL;

-- EN: Saved chat replies, at most ten per person. / VI: Câu trả lời nhanh đã lưu, tối đa mười câu mỗi người.
CREATE TABLE quick_replies (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body       VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_quick_replies_user ON quick_replies (user_id, created_at);
