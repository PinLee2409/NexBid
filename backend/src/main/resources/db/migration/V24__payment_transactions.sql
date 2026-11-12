-- EN: One row per attempt to pay through VNPay. Each attempt has its own vnp_TxnRef, so a failed try and the retry
--     never collide, and a repeated IPN for the same attempt finds the row already settled.
-- VI: Mỗi lần thử thanh toán qua VNPay là một dòng. Mỗi lần thử có vnp_TxnRef riêng, nên lần hỏng và lần thử lại không
--     bao giờ đụng nhau, và IPN gửi lặp cho cùng một lần thử sẽ thấy dòng đã được chốt.
CREATE TABLE payment_transactions (
    id             UUID PRIMARY KEY,
    payment_id     UUID          NOT NULL REFERENCES payments (id),
    txn_ref        VARCHAR(40)   NOT NULL,
    amount         NUMERIC(15,2) NOT NULL,
    status         VARCHAR(16)   NOT NULL,
    response_code  VARCHAR(8),
    transaction_no VARCHAR(40),
    bank_code      VARCHAR(32),
    created_at     TIMESTAMPTZ   NOT NULL,
    updated_at     TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_payment_transactions_txn_ref UNIQUE (txn_ref),
    CONSTRAINT ck_payment_transactions_status CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED'))
);

CREATE INDEX ix_payment_transactions_payment ON payment_transactions (payment_id);
