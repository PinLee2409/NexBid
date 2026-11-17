-- EN: Shipping vouchers an admin hands out: a code takes a fixed amount off the buyer's shipping (never more than the
--     shipping), once per buyer, up to a number of uses and a date. The seller still receives the full shipping; the
--     platform pays the difference. A code counts as used when the payment succeeds.
-- VI: Voucher phí ship do admin phát: một mã giảm một số tiền cố định vào phí ship của người mua (không quá phí ship),
--     mỗi người mua một lần, tới một số lượt và một ngày. Người bán vẫn nhận đủ phí ship; sàn trả phần chênh. Mã tính là
--     đã dùng khi thanh toán thành công.
CREATE TABLE promo_codes (
    code       VARCHAR(32) PRIMARY KEY,
    amount     NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    max_uses   INTEGER NOT NULL CHECK (max_uses > 0),
    expires_at TIMESTAMPTZ,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    note       VARCHAR(200),
    created_by UUID REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE promo_redemptions (
    code       VARCHAR(32) NOT NULL REFERENCES promo_codes (code),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    payment_id UUID NOT NULL UNIQUE REFERENCES payments (id),
    discount   NUMERIC(15, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (code, user_id)
);

ALTER TABLE payments ADD COLUMN shipping_discount NUMERIC(15, 2) NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN promo_code VARCHAR(32);
ALTER TABLE payments ADD CONSTRAINT payments_shipping_discount_non_negative CHECK (shipping_discount >= 0);
