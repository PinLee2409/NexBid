-- EN: NexBid books GHN pickups itself. The buyer's GHN district and ward are copied onto the order with the rest of the
--     address when they pay; an order booked through NexBid's GHN account records what GHN charged the platform.
-- VI: NexBid tự đặt GHN lấy hàng. Quận và phường GHN của người mua được chép lên đơn cùng phần còn lại của địa chỉ khi
--     họ thanh toán; đơn đặt qua tài khoản GHN của NexBid ghi lại số GHN thu của sàn.
ALTER TABLE orders ADD COLUMN ship_district_id INTEGER;
ALTER TABLE orders ADD COLUMN ship_ward_code VARCHAR(20);
ALTER TABLE orders ADD COLUMN ghn_booked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN carrier_fee NUMERIC(15, 2);
