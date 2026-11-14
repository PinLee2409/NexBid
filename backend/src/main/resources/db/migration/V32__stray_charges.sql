-- EN: Money VNPay took that NexBid could not use: an attempt confirmed after the payment window closed, or a second
--     attempt on a payment already paid. Each waits here until an admin transfers it back by hand.
-- VI: Tiền VNPay đã thu mà NexBid không dùng được: lần trả được xác nhận sau khi hết hạn thanh toán, hoặc lần trả thứ
--     hai cho khoản đã trả. Mỗi khoản nằm ở đây cho tới khi admin tự chuyển khoản trả lại.
CREATE TABLE stray_charges (
    id              UUID          PRIMARY KEY,
    payment_id      UUID          NOT NULL REFERENCES payments (id),
    txn_ref         VARCHAR(40)   NOT NULL UNIQUE,
    amount          NUMERIC(15,2) NOT NULL,
    reason          VARCHAR(16)   NOT NULL CHECK (reason IN ('LATE', 'DUPLICATE')),
    created_at      TIMESTAMPTZ   NOT NULL,
    refunded_at     TIMESTAMPTZ,
    reference       VARCHAR(80),
    bank_name       VARCHAR(120),
    account_number  VARCHAR(40),
    account_holder  VARCHAR(120),
    refunded_by     UUID
);
CREATE INDEX ix_stray_charges_open ON stray_charges (created_at) WHERE refunded_at IS NULL;
