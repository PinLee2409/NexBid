-- EN: An append-only record of important actions. Actor ids are deliberately not foreign keys:
--     deleting an account must not erase the history of what it did.
-- VI: Nhật ký chỉ ghi thêm cho các hành động quan trọng. Cố ý không đặt khoá ngoại cho người thực hiện:
--     xoá tài khoản không được xoá lịch sử những việc tài khoản ấy đã làm.
CREATE TABLE audit_logs (
    id          UUID         PRIMARY KEY,
    position    BIGINT       GENERATED ALWAYS AS IDENTITY UNIQUE,
    user_id     UUID,
    action      VARCHAR(40)  NOT NULL,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   UUID         NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_audit_logs_recent ON audit_logs (position DESC);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id, created_at DESC);
