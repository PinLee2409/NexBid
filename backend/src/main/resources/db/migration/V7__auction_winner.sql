-- EN: Winner selection (guide §27, spec §16). The auction row tracks who leads, updated under the same
--     lock as the price, so at the close the winner is read off the row rather than searched for.
-- VI: Chọn người thắng (guide §27, spec §16). Dòng auction theo dõi ai đang dẫn, cập nhật dưới cùng khoá
--     với giá, nên lúc đóng phiên người thắng được đọc thẳng từ dòng đó thay vì phải đi tìm.

ALTER TABLE auctions ADD COLUMN leading_bidder_id UUID REFERENCES users (id);

-- EN: Existing lots: the leader is whoever placed the highest bid. Amounts are unique per lot, so there
--     is exactly one.
-- VI: Các lô đã có: người dẫn là người đặt lượt cao nhất. Số tiền là duy nhất trong một lô, nên chỉ có
--     đúng một người.
UPDATE auctions a
SET leading_bidder_id = top.bidder_id
FROM (
    SELECT DISTINCT ON (auction_id) auction_id, bidder_id
    FROM bids
    ORDER BY auction_id, amount DESC
) top
WHERE top.auction_id = a.id;

-- EN: Lots that were closed before winners were chosen get theirs now.
-- VI: Các lô đã đóng trước khi có bước chọn người thắng thì giờ được gán người thắng.
UPDATE auctions
SET winner_id = leading_bidder_id
WHERE status IN ('ENDED', 'COMPLETED') AND winner_id IS NULL AND leading_bidder_id IS NOT NULL;

-- EN: A lot that ended with no bids sold nothing, so it stops holding its product — the same as a
--     rejected or cancelled one. Without this the product could never be listed again.
-- VI: Lô kết thúc mà không có lượt trả giá nào là chưa bán được gì, nên nó thôi giữ sản phẩm — giống lô
--     bị từ chối hay bị huỷ. Không có dòng này thì sản phẩm không bao giờ đăng lại được.
DROP INDEX ux_auctions_one_live_per_product;

CREATE UNIQUE INDEX ux_auctions_one_live_per_product
    ON auctions (product_id)
    WHERE status IN ('DRAFT', 'PENDING_APPROVAL', 'SCHEDULED', 'ACTIVE')
       OR (status = 'ENDED' AND winner_id IS NOT NULL);

UPDATE products p
SET status = 'AVAILABLE'
FROM auctions a
WHERE a.product_id = p.id
  AND a.status = 'ENDED'
  AND a.winner_id IS NULL
  AND p.status = 'IN_AUCTION';
