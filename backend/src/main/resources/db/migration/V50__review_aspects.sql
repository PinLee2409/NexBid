-- EN: Optional stars for three parts of a sale: packing, the goods matching the description, and shipping speed.
-- VI: Số sao tuỳ chọn cho ba khía cạnh của giao dịch: đóng gói, hàng đúng mô tả, và tốc độ gửi hàng.
ALTER TABLE seller_reviews ADD COLUMN packaging SMALLINT CHECK (packaging BETWEEN 1 AND 5);
ALTER TABLE seller_reviews ADD COLUMN accuracy SMALLINT CHECK (accuracy BETWEEN 1 AND 5);
ALTER TABLE seller_reviews ADD COLUMN speed SMALLINT CHECK (speed BETWEEN 1 AND 5);
