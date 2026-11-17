-- EN: A seller can offer free shipping: the buyer pays only the goods, and the seller pays the parcel. When NexBid books
--     GHN for such an order, what GHN charged is taken from the seller's payout.
-- VI: Người bán có thể miễn phí vận chuyển: người mua chỉ trả tiền hàng, người bán trả tiền gửi kiện. Khi NexBid đặt GHN
--     cho đơn như vậy, số GHN thu được trừ vào tiền chi cho người bán.
ALTER TABLE auctions ADD COLUMN free_shipping BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE orders ADD COLUMN seller_pays_shipping BOOLEAN NOT NULL DEFAULT FALSE;
