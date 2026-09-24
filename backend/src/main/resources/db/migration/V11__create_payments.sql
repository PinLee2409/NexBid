-- EN: Payments (guide §32, spec §17, columns from spec §23). A financial record, so nothing cascades
--     into it: deleting a user or a lot that has a payment is refused.
-- VI: Thanh toán (guide §32, spec §17, các cột theo spec §23). Là bản ghi tài chính nên không có gì xoá
--     lan xuống: xoá user hay lô đã có thanh toán đều bị từ chối.

CREATE TABLE payments (
    id         UUID          PRIMARY KEY,
    auction_id UUID          NOT NULL REFERENCES auctions (id) ON DELETE RESTRICT,
    user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    amount     NUMERIC(15,2) NOT NULL CHECK (amount > 0),
    status     VARCHAR(16)   NOT NULL,
    expired_at TIMESTAMPTZ   NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,

    -- EN: One payment per lot: a close that ran twice cannot bill the winner twice.
    -- VI: Mỗi lô một khoản thanh toán: việc đóng phiên có chạy hai lần cũng không bắt người thắng trả hai lần.
    CONSTRAINT ux_payments_auction UNIQUE (auction_id)
);

CREATE INDEX ix_payments_user ON payments (user_id, created_at DESC);

-- EN: What the expiry job scans: still open, oldest deadline first.
-- VI: Thứ mà job hết hạn quét: còn mở, hạn chót cũ nhất trước.
CREATE INDEX ix_payments_open_by_deadline ON payments (expired_at) WHERE status IN ('PENDING', 'FAILED');

-- EN: Lots that already closed with a winner before payments existed get theirs now, on the same
--     48-hour window the application uses (nexbid.payment.window).
-- VI: Các lô đã đóng có người thắng từ trước khi có thanh toán thì giờ được tạo, với cùng khung 48 giờ mà
--     ứng dụng dùng (nexbid.payment.window).
INSERT INTO payments (id, auction_id, user_id, amount, status, expired_at, created_at, updated_at)
SELECT gen_random_uuid(), a.id, a.winner_id, a.current_price, 'PENDING',
       a.end_time + interval '48 hours', now(), now()
FROM auctions a
WHERE a.status = 'ENDED' AND a.winner_id IS NOT NULL;
