-- EN: Where signed-in people act from: a hash of their network address and the device id their browser keeps. Only
--     compared between accounts, to spot a seller bidding on their own lots under another name.
-- VI: Người đăng nhập thao tác từ đâu: mã băm địa chỉ mạng và mã thiết bị trình duyệt giữ. Chỉ dùng để so giữa các tài
--     khoản, nhằm phát hiện người bán tự trả giá lô của mình bằng tên khác.
CREATE TABLE user_origins (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- EN: 'NETWORK' or 'DEVICE'. / VI: 'NETWORK' hoặc 'DEVICE'.
    kind       VARCHAR(8)  NOT NULL,
    value      VARCHAR(64) NOT NULL,
    first_seen TIMESTAMPTZ NOT NULL,
    last_seen  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, kind, value)
);

CREATE INDEX ix_user_origins_value ON user_origins (kind, value);

-- EN: An admin looked at a seller–bidder pair and found nothing wrong; it stays hidden until the bidder bids on that
--     seller's lots again.
-- VI: Admin đã xem một cặp người bán–người trả giá và thấy không có gì sai; cặp đó ẩn đi tới khi người trả giá lại trả
--     giá lô của người bán đó.
CREATE TABLE shill_reviews (
    seller_id  UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    bidder_id  UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    decided_by UUID          NOT NULL REFERENCES users (id),
    decided_at TIMESTAMPTZ   NOT NULL,
    note       VARCHAR(500),
    PRIMARY KEY (seller_id, bidder_id)
);
