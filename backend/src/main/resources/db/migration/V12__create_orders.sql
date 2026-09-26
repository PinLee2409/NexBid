-- EN: Orders (guide §33, spec §16, columns from spec §23). The record of a sale; the payment is the act.
--     A financial record like payments, so nothing cascades into it.
-- VI: Đơn hàng (guide §33, spec §16, các cột theo spec §23). Là bản ghi của một giao dịch; thanh toán là hành
--     động. Là bản ghi tài chính như thanh toán, nên không có gì xoá lan xuống.

CREATE TABLE orders (
    id         UUID          PRIMARY KEY,
    auction_id UUID          NOT NULL REFERENCES auctions (id) ON DELETE RESTRICT,
    buyer_id   UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    seller_id  UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    payment_id UUID          NOT NULL REFERENCES payments (id) ON DELETE RESTRICT,
    amount     NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    status     VARCHAR(20)   NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,

    -- EN: One sale per lot, one order per payment. / VI: Mỗi lô một giao dịch, mỗi khoản thanh toán một đơn.
    CONSTRAINT ux_orders_auction UNIQUE (auction_id),
    CONSTRAINT ux_orders_payment UNIQUE (payment_id)
);

CREATE INDEX ix_orders_buyer ON orders (buyer_id, created_at DESC);
CREATE INDEX ix_orders_seller ON orders (seller_id, created_at DESC);

-- EN: Every payment that already exists gets its order, in the state its payment is in.
-- VI: Mọi khoản thanh toán đã có đều được tạo đơn, theo đúng trạng thái của khoản thanh toán đó.
INSERT INTO orders (id, auction_id, buyer_id, seller_id, payment_id, amount, status, created_at, updated_at)
SELECT gen_random_uuid(), p.auction_id, p.user_id, a.seller_id, p.id, p.amount,
       CASE p.status WHEN 'SUCCESS' THEN 'PAID' WHEN 'EXPIRED' THEN 'CANCELLED' ELSE 'PENDING_PAYMENT' END,
       p.created_at, now()
FROM payments p
JOIN auctions a ON a.id = p.auction_id;

-- EN: A sale paid for before orders existed is completed now, exactly as a new payment would complete it.
-- VI: Giao dịch đã được trả trước khi có đơn hàng thì giờ được hoàn tất, y như một lần thanh toán mới.
UPDATE products pr
SET status = 'SOLD', updated_at = now()
FROM auctions a
JOIN payments p ON p.auction_id = a.id
WHERE a.product_id = pr.id AND p.status = 'SUCCESS' AND a.status = 'ENDED';

UPDATE auctions a
SET status = 'COMPLETED', updated_at = now()
FROM payments p
WHERE p.auction_id = a.id AND p.status = 'SUCCESS' AND a.status = 'ENDED';
