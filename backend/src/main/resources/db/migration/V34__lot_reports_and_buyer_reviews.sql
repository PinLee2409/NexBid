-- EN: Reports about a lot (counterfeit, not as described, prohibited, other). One open report per person per lot. An
--     admin dismisses a report, or takes the lot down, which closes every open report on it.
-- VI: Báo cáo về một lô (hàng giả, sai mô tả, hàng cấm, khác). Mỗi người một báo cáo đang mở trên một lô. Admin bỏ qua
--     báo cáo, hoặc gỡ lô, việc này đóng mọi báo cáo đang mở của lô đó.
CREATE TABLE lot_reports (
    id           UUID          PRIMARY KEY,
    auction_id   UUID          NOT NULL REFERENCES auctions (id) ON DELETE CASCADE,
    reporter_id  UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    reason       VARCHAR(24)   NOT NULL CHECK (reason IN ('COUNTERFEIT', 'NOT_AS_DESCRIBED', 'PROHIBITED', 'OTHER')),
    details      VARCHAR(1000) NOT NULL,
    status       VARCHAR(16)   NOT NULL CHECK (status IN ('OPEN', 'DISMISSED', 'REMOVED')),
    note         VARCHAR(500),
    created_at   TIMESTAMPTZ   NOT NULL,
    resolved_at  TIMESTAMPTZ,
    resolved_by  UUID
);
CREATE UNIQUE INDEX ux_lot_reports_open ON lot_reports (auction_id, reporter_id) WHERE status = 'OPEN';
CREATE INDEX ix_lot_reports_status ON lot_reports (status, created_at);

-- EN: The seller's rating of the buyer, once per completed order. / VI: Người bán đánh giá người mua, một lần mỗi đơn hoàn tất.
CREATE TABLE buyer_reviews (
    id          UUID          PRIMARY KEY,
    order_id    UUID          NOT NULL UNIQUE REFERENCES orders (id) ON DELETE CASCADE,
    auction_id  UUID          NOT NULL,
    seller_id   UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    buyer_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    rating      SMALLINT      NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment     VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_buyer_reviews_buyer ON buyer_reviews (buyer_id, created_at DESC);

-- EN: Wins a person never paid for, counted as each payment expires. Old ones are counted once here.
-- VI: Số lần thắng mà không trả tiền, đếm mỗi khi một khoản thanh toán hết hạn. Các lần cũ được đếm một lần ở đây.
ALTER TABLE users ADD COLUMN unpaid_wins INT NOT NULL DEFAULT 0;
UPDATE users u SET unpaid_wins = p.n
FROM (SELECT user_id, COUNT(*) AS n FROM payments WHERE status = 'EXPIRED' GROUP BY user_id) p
WHERE u.id = p.user_id;
