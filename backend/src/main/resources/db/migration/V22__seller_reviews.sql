-- EN: Buyers rate sellers (outside the spec, chosen by the product owner): one review per completed order, 1 to 5
--     stars with an optional short comment, never edited. The seller's average is worked out from these rows.
-- VI: Người mua đánh giá người bán (ngoài đặc tả, do chủ dự án chọn): mỗi đơn đã hoàn tất một đánh giá, 1 tới 5 sao
--     kèm nhận xét ngắn tuỳ chọn, không sửa được. Điểm trung bình của người bán tính từ các dòng này.
CREATE TABLE seller_reviews (
    id          UUID         PRIMARY KEY,
    order_id    UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    auction_id  UUID         NOT NULL,
    seller_id   UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    buyer_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating      SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(500),
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ux_seller_reviews_order UNIQUE (order_id)
);

CREATE INDEX ix_seller_reviews_seller ON seller_reviews (seller_id, created_at DESC);
