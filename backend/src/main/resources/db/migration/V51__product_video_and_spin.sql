-- EN: One short video per product, and a flag that its photos, in order, are frames of a full turn (a 360° view).
-- VI: Mỗi sản phẩm một video ngắn, và một cờ cho biết các ảnh, theo thứ tự, là các khung hình của một vòng xoay (xem 360°).
ALTER TABLE products ADD COLUMN video_url VARCHAR(300);
ALTER TABLE products ADD COLUMN spin_view BOOLEAN NOT NULL DEFAULT FALSE;
