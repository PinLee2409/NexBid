-- EN: Which language a person's emails are written in: Vietnamese, English, or both (the old behaviour, kept for
--     accounts made before the choice existed). New accounts take the language of the page they signed up on.
-- VI: Email của một người được viết bằng ngôn ngữ nào: tiếng Việt, tiếng Anh, hoặc cả hai (cách cũ, giữ cho các tài
--     khoản tạo trước khi có lựa chọn này). Tài khoản mới lấy ngôn ngữ của trang mà họ đăng ký.
ALTER TABLE users ADD COLUMN email_language VARCHAR(4) NOT NULL DEFAULT 'both'
    CONSTRAINT ck_users_email_language CHECK (email_language IN ('vi', 'en', 'both'));
