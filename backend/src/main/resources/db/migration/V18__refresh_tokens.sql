-- EN: Refresh tokens (spec §7.2, "Refresh Token nếu triển khai"). Only a SHA-256 of each token is kept, so a copy
--     of this table cannot sign anyone in. Every sign-in starts a family; each refresh replaces the token with a
--     new one in the same family, and a replaced token coming back revokes the whole family.
-- VI: Refresh token (spec §7.2, "Refresh Token nếu triển khai"). Chỉ lưu SHA-256 của mỗi token, nên có bản sao bảng
--     này cũng không đăng nhập được thay ai. Mỗi lần đăng nhập mở một họ token; mỗi lần refresh thay token bằng một
--     token mới cùng họ, và token đã bị thay mà quay lại thì cả họ bị thu hồi.
CREATE TABLE refresh_tokens (
    id          UUID        PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id   UUID        NOT NULL,
    token_hash  VARCHAR(64) NOT NULL,
    -- EN: "Remember me": the cookie outlives the browser. / VI: "Remember me": cookie sống qua lần tắt trình duyệt.
    remember    BOOLEAN     NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    -- EN: Set when a refresh replaced this token; a revoked token without it was signed out or blocked.
    -- VI: Có giá trị khi một lần refresh đã thay token này; token bị thu hồi mà không có là do đăng xuất hoặc bị khoá.
    replaced_by UUID,
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_live_by_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_refresh_tokens_expires ON refresh_tokens (expires_at);
