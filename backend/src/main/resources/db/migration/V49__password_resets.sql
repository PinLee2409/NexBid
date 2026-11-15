-- EN: Reset links: only a hash of the token is kept; each is good for 30 minutes and once.
-- VI: Link đặt lại mật khẩu: chỉ giữ mã băm của token; mỗi link dùng được 30 phút và một lần.
CREATE TABLE password_resets (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_password_resets_user ON password_resets (user_id);
