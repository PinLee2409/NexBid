-- EN: Accounts, roles and the join between them (spec §23, migration order §44).
-- VI: Tài khoản, vai trò và bảng nối giữa chúng (spec §23, thứ tự migration §44).

CREATE TABLE roles (
    id   SERIAL PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE
);

CREATE TABLE users (
    id         UUID         PRIMARY KEY,
    full_name  VARCHAR(120) NOT NULL,
    email      VARCHAR(254) NOT NULL,
    -- EN: Wide enough for a BCrypt hash; the plain password is never stored.
    -- VI: Đủ rộng cho hash BCrypt; mật khẩu gốc không bao giờ được lưu.
    password   VARCHAR(100) NOT NULL,
    status     VARCHAR(16)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

-- EN: Email is the login identifier and is matched case-insensitively, so the
--     uniqueness rule has to ignore case too — otherwise A@x.com and a@x.com both register.
-- VI: Email là định danh đăng nhập và so khớp không phân biệt hoa thường, nên ràng buộc
--     duy nhất cũng phải bỏ qua hoa thường — nếu không A@x.com và a@x.com đều đăng ký được.
CREATE UNIQUE INDEX ux_users_email_lower ON users (LOWER(email));

CREATE TABLE user_roles (
    user_id UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id INTEGER NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

-- EN: The three roles are fixed by the specification, so they belong to the schema, not to seed data.
-- VI: Ba vai trò do đặc tả quy định cố định, nên thuộc về schema chứ không phải dữ liệu mẫu.
INSERT INTO roles (name) VALUES ('BUYER'), ('SELLER'), ('ADMIN');
