-- EN: Browser push. The VAPID key pair is made once, on first use, and kept here so every instance signs alike.
--     Each subscription is one browser of one user; the push service's endpoint is unique.
-- VI: Thông báo đẩy trên trình duyệt. Cặp khoá VAPID được tạo một lần, khi dùng lần đầu, và giữ ở đây để mọi instance ký
--     giống nhau. Mỗi đăng ký là một trình duyệt của một người dùng; endpoint của dịch vụ push là duy nhất.
CREATE TABLE push_keys (
    id          SMALLINT PRIMARY KEY CHECK (id = 1),
    public_key  TEXT NOT NULL,
    private_key TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE push_subscriptions (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint     TEXT NOT NULL UNIQUE,
    p256dh       TEXT NOT NULL,
    auth         TEXT NOT NULL,
    locale       VARCHAR(5) NOT NULL DEFAULT 'vi',
    created_at   TIMESTAMPTZ NOT NULL,
    last_sent_at TIMESTAMPTZ
);

CREATE INDEX ix_push_subscriptions_user ON push_subscriptions (user_id);
