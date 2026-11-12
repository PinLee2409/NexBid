-- EN: Where a buyer wants their lots delivered: one address per account, edited from the profile. Checkout refuses to
--     start without one, and a paid order keeps its own copy, so a later edit never moves a parcel already on its way.
-- VI: Nơi người mua muốn nhận hàng: mỗi tài khoản một địa chỉ, sửa trong hồ sơ. Checkout không chịu bắt đầu khi chưa có,
--     và đơn đã trả giữ bản sao riêng, nên sửa sau này không bao giờ đổi hướng một kiện hàng đang đi.
CREATE TABLE user_addresses (
    user_id    UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    recipient  VARCHAR(120) NOT NULL,
    phone      VARCHAR(20)  NOT NULL,
    line       VARCHAR(255) NOT NULL,
    ward       VARCHAR(120),
    district   VARCHAR(120),
    province   VARCHAR(120) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

ALTER TABLE orders
    ADD COLUMN ship_recipient  VARCHAR(120),
    ADD COLUMN ship_phone      VARCHAR(20),
    ADD COLUMN ship_address    VARCHAR(640),
    ADD COLUMN carrier         VARCHAR(60),
    ADD COLUMN tracking_number VARCHAR(60),
    ADD COLUMN shipped_at      TIMESTAMPTZ;

-- EN: Outbid emails are the only optional ones; everything else is about money or goods the person is owed.
-- VI: Email bị vượt giá là loại duy nhất có thể tắt; mọi loại khác đều liên quan tới tiền hoặc hàng của người đó.
ALTER TABLE users ADD COLUMN outbid_emails BOOLEAN NOT NULL DEFAULT TRUE;

-- EN: The one-click unsubscribe address of an optional email, sent as its List-Unsubscribe header.
-- VI: Địa chỉ huỷ đăng ký một chạm của email có thể tắt, gửi đi dưới dạng header List-Unsubscribe.
ALTER TABLE email_outbox ADD COLUMN unsubscribe_url VARCHAR(500);
