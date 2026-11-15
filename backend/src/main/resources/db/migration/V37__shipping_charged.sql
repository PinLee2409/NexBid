-- EN: Shipping is charged with the goods. The quote is fixed on the payment the first time the buyer opens checkout
--     and kept until they change address; GHN when both addresses are on GHN, otherwise a flat fee.
-- VI: Phí ship được thu cùng tiền hàng. Phí được chốt trên khoản thanh toán lần đầu người mua mở thanh toán và giữ
--     nguyên tới khi họ đổi địa chỉ; tính theo GHN khi cả hai địa chỉ có mã GHN, không thì lấy phí cố định.
ALTER TABLE payments ADD COLUMN shipping_fee NUMERIC(15, 2) NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN shipping_source VARCHAR(8);
ALTER TABLE payments ADD COLUMN shipping_address_key VARCHAR(64);
ALTER TABLE payments ADD CONSTRAINT payments_shipping_fee_non_negative CHECK (shipping_fee >= 0);

ALTER TABLE orders ADD COLUMN shipping_fee NUMERIC(15, 2) NOT NULL DEFAULT 0;

-- EN: The seller ships, so the shipping paid comes back to them on top of the goods; the platform fee is on the goods.
-- VI: Người bán gửi hàng, nên phí ship người mua đã trả được trả lại cho họ cùng tiền hàng; phí sàn chỉ tính trên tiền hàng.
ALTER TABLE seller_earnings ADD COLUMN shipping NUMERIC(15, 2) NOT NULL DEFAULT 0;
