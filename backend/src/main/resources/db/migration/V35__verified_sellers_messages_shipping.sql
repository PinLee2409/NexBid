-- EN: A seller whose papers an admin checked; the lot page shows a badge.
-- VI: Người bán đã được admin xác minh giấy tờ; trang lô hiện huy hiệu.
ALTER TABLE users ADD COLUMN seller_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- EN: Messages between the buyer and the seller of one order. read_at is set when the other side opens the thread.
-- VI: Tin nhắn giữa người mua và người bán của một đơn. read_at được đặt khi bên kia mở cuộc trò chuyện.
CREATE TABLE order_messages (
    id          UUID          PRIMARY KEY,
    order_id    UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    sender_id   UUID          NOT NULL REFERENCES users (id),
    body        VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    read_at     TIMESTAMPTZ
);
CREATE INDEX ix_order_messages_order ON order_messages (order_id, created_at);
CREATE INDEX ix_order_messages_unread ON order_messages (order_id) WHERE read_at IS NULL;

-- EN: What the carrier last reported, and when it said the parcel was delivered (GHN, when connected).
-- VI: Trạng thái đơn vị vận chuyển báo gần nhất, và lúc họ báo đã giao (GHN, khi đã kết nối).
ALTER TABLE orders ADD COLUMN shipping_status VARCHAR(40);
ALTER TABLE orders ADD COLUMN delivered_at TIMESTAMPTZ;
