-- EN: Emails waiting to go out through Brevo. A row is written in the same transaction as the event it reports, so an
--     email exists exactly when the event committed; a job sends due rows and retries failures with backoff.
-- VI: Email chờ gửi qua Brevo. Mỗi dòng được ghi trong cùng transaction với sự kiện nó báo, nên email tồn tại đúng khi
--     sự kiện đã commit; một job gửi các dòng tới hạn và thử lại khi lỗi, giãn dần thời gian chờ.
CREATE TABLE email_outbox (
    id              UUID PRIMARY KEY,
    user_id         UUID,
    to_email        VARCHAR(254) NOT NULL,
    to_name         VARCHAR(120),
    kind            VARCHAR(40)  NOT NULL,
    auction_id      UUID,
    subject         VARCHAR(255) NOT NULL,
    html            TEXT         NOT NULL,
    text            TEXT         NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    attempts        INT          NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    last_error      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL,
    sent_at         TIMESTAMPTZ,
    CONSTRAINT ck_email_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

-- EN: The sender's queue. / VI: Hàng đợi của job gửi.
CREATE INDEX ix_email_outbox_due ON email_outbox (next_attempt_at) WHERE status = 'PENDING';
-- EN: "Did we already email this person about this lot recently?" for the outbid throttle.
-- VI: "Gần đây đã gửi email cho người này về lô này chưa?" cho giới hạn email bị vượt giá.
CREATE INDEX ix_email_outbox_recent ON email_outbox (user_id, auction_id, kind, created_at);
