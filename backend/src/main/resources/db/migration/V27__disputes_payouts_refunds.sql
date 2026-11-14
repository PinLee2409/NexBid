-- EN: After the payment: orders complete on their own after delivery, buyers can dispute, sellers get paid out,
--     and refunds go back through VNPay.
-- VI: Sau khi thanh toán: đơn tự hoàn tất sau khi giao, người mua có thể khiếu nại, người bán được chi trả, và tiền
--     hoàn đi ngược qua VNPay.

-- EN: When the order completed, and whether the buyer confirmed it or the clock did.
-- VI: Đơn hoàn tất khi nào, và do người mua xác nhận hay do hết thời gian chờ.
ALTER TABLE orders ADD COLUMN completed_at TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN auto_completed BOOLEAN NOT NULL DEFAULT FALSE;
CREATE INDEX ix_orders_processing_shipped ON orders (shipped_at) WHERE status = 'PROCESSING';

-- EN: A buyer's complaint about one order. At most one open at a time.
-- VI: Khiếu nại của người mua về một đơn. Mỗi lúc tối đa một khiếu nại đang mở.
CREATE TABLE order_disputes (
    id              UUID PRIMARY KEY,
    order_id        UUID         NOT NULL REFERENCES orders (id),
    buyer_id        UUID         NOT NULL REFERENCES users (id),
    reason          VARCHAR(30)  NOT NULL,
    details         VARCHAR(1000) NOT NULL,
    -- EN: Where the order goes back to when the dispute is turned down. / VI: Đơn quay về đâu khi khiếu nại bị bác.
    prior_status    VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    resolution_note VARCHAR(1000),
    resolved_by     UUID REFERENCES users (id),
    created_at      TIMESTAMPTZ  NOT NULL,
    resolved_at     TIMESTAMPTZ,
    CONSTRAINT ck_order_disputes_reason CHECK (reason IN ('NOT_RECEIVED', 'NOT_AS_DESCRIBED', 'DAMAGED', 'OTHER')),
    CONSTRAINT ck_order_disputes_status CHECK (status IN ('OPEN', 'REFUNDED', 'REJECTED'))
);
CREATE UNIQUE INDEX ux_order_disputes_open ON order_disputes (order_id) WHERE status = 'OPEN';
CREATE INDEX ix_order_disputes_status ON order_disputes (status, created_at);

-- EN: Where a seller wants their money sent. / VI: Nơi người bán muốn nhận tiền.
CREATE TABLE seller_bank_accounts (
    user_id        UUID PRIMARY KEY REFERENCES users (id),
    bank_name      VARCHAR(120) NOT NULL,
    account_number VARCHAR(40)  NOT NULL,
    account_holder VARCHAR(120) NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

-- EN: One transfer the platform made to a seller, covering one or more completed orders.
-- VI: Một lần sàn chuyển tiền cho người bán, gồm một hoặc nhiều đơn đã hoàn tất.
CREATE TABLE seller_payouts (
    id             UUID PRIMARY KEY,
    seller_id      UUID          NOT NULL REFERENCES users (id),
    amount         NUMERIC(15, 2) NOT NULL,
    bank_name      VARCHAR(120)  NOT NULL,
    account_number VARCHAR(40)   NOT NULL,
    account_holder VARCHAR(120)  NOT NULL,
    reference      VARCHAR(80)   NOT NULL,
    paid_by        UUID          NOT NULL REFERENCES users (id),
    created_at     TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_seller_payouts_seller ON seller_payouts (seller_id, created_at DESC);

-- EN: What the platform owes a seller for one completed order: the price less the platform's fee.
-- VI: Số sàn nợ người bán cho một đơn đã hoàn tất: giá bán trừ phí sàn.
CREATE TABLE seller_earnings (
    id         UUID PRIMARY KEY,
    order_id   UUID          NOT NULL UNIQUE REFERENCES orders (id),
    seller_id  UUID          NOT NULL REFERENCES users (id),
    auction_id UUID          NOT NULL,
    gross      NUMERIC(15, 2) NOT NULL,
    fee        NUMERIC(15, 2) NOT NULL,
    net        NUMERIC(15, 2) NOT NULL,
    payout_id  UUID REFERENCES seller_payouts (id),
    created_at TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_seller_earnings_unpaid ON seller_earnings (seller_id) WHERE payout_id IS NULL;

-- EN: Each refund and how the money went back: through VNPay's API, or by hand when there was no gateway charge.
-- VI: Mỗi lần hoàn tiền và tiền đã về bằng cách nào: qua API của VNPay, hoặc thủ công khi không có giao dịch cổng.
CREATE TABLE payment_refunds (
    id             UUID PRIMARY KEY,
    payment_id     UUID         NOT NULL UNIQUE REFERENCES payments (id),
    amount         NUMERIC(15, 2) NOT NULL,
    method         VARCHAR(16)  NOT NULL,
    request_id     VARCHAR(40),
    response_code  VARCHAR(8),
    transaction_no VARCHAR(40),
    created_by     UUID REFERENCES users (id),
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_payment_refunds_method CHECK (method IN ('VNPAY', 'MANUAL'))
);
