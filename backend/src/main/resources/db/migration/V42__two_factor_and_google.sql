-- EN: Two-step sign-in with an authenticator app (TOTP, RFC 6238). The secret is encrypted with a key derived from the
--     server's JWT secret; last_step stops a code from being used twice; recovery codes are stored as SHA-256 hashes
--     and each works once. enabled_at is null while the person is still setting it up.
-- VI: Đăng nhập hai bước bằng ứng dụng xác thực (TOTP, RFC 6238). Secret được mã hoá bằng khoá suy ra từ JWT secret của
--     server; last_step chặn dùng một mã hai lần; mã khôi phục lưu dạng băm SHA-256 và mỗi mã dùng được một lần.
--     enabled_at là null khi người dùng còn đang cài đặt.
CREATE TABLE user_totp (
    user_id        UUID         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    secret         VARCHAR(200) NOT NULL,
    enabled_at     TIMESTAMPTZ,
    last_step      BIGINT       NOT NULL DEFAULT 0,
    recovery_codes TEXT[]       NOT NULL DEFAULT '{}'
);

-- EN: Accounts signed in through Google, by Google's stable subject id (an email address can change).
-- VI: Tài khoản đăng nhập qua Google, theo subject id cố định của Google (email có thể đổi).
CREATE TABLE user_identities (
    provider   VARCHAR(20)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (provider, subject)
);
