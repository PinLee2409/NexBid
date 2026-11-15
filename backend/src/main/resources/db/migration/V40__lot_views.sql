-- EN: Who looked at a lot, once per viewer per day: a signed-in user by id, anyone else by a hash of their address.
--     Counted for the seller's lot statistics; the seller's own visits are not recorded.
-- VI: Ai đã xem một lô, mỗi người xem một lần mỗi ngày: người đăng nhập theo id, người khác theo mã băm địa chỉ.
--     Dùng cho thống kê lô của người bán; người bán tự xem lô của mình không được ghi.
CREATE TABLE auction_views (
    auction_id UUID        NOT NULL REFERENCES auctions (id) ON DELETE CASCADE,
    viewer_key VARCHAR(80) NOT NULL,
    day        DATE        NOT NULL,
    PRIMARY KEY (auction_id, day, viewer_key)
);
