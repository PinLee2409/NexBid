-- EN: The packed size of a product, for shipping quotes. All four or none.
-- VI: Kích thước kiện của sản phẩm, để báo phí ship. Đủ cả bốn hoặc không có.
ALTER TABLE products
    ADD COLUMN weight_grams INTEGER,
    ADD COLUMN length_cm    INTEGER,
    ADD COLUMN width_cm     INTEGER,
    ADD COLUMN height_cm    INTEGER,
    ADD CONSTRAINT products_parcel_complete CHECK (
        (weight_grams IS NULL AND length_cm IS NULL AND width_cm IS NULL AND height_cm IS NULL)
        OR (weight_grams IS NOT NULL AND length_cm IS NOT NULL AND width_cm IS NOT NULL AND height_cm IS NOT NULL));

-- EN: GHN's ids for the address, when it was picked from GHN's lists. The names stay for the label.
-- VI: Mã GHN của địa chỉ, khi được chọn từ danh sách của GHN. Tên vẫn giữ để in nhãn.
ALTER TABLE user_addresses
    ADD COLUMN province_id INTEGER,
    ADD COLUMN district_id INTEGER,
    ADD COLUMN ward_code   VARCHAR(20);
