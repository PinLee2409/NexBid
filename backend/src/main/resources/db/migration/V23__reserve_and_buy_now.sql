-- EN: A hidden reserve price and a buy-now price (outside the spec, chosen by the product owner). Bidders learn only
--     whether the reserve has been met. A lot that closes below it waits up to 24 hours for the seller to accept or
--     refuse the highest bid. Buy-now sells the lot at once, until the first bid is placed.
-- VI: Giá sàn ẩn và giá mua ngay (ngoài đặc tả, do chủ dự án chọn). Người trả giá chỉ biết giá sàn đã đạt hay chưa.
--     Lô đóng dưới giá sàn chờ tối đa 24 giờ để người bán nhận hoặc từ chối giá cao nhất. Mua ngay bán lô tức thì,
--     cho tới khi có lượt trả giá đầu tiên.
ALTER TABLE auctions
    ADD COLUMN reserve_price          NUMERIC(15,2),
    ADD COLUMN buy_now_price          NUMERIC(15,2),
    ADD COLUMN bought_now             BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reserve_decision       VARCHAR(16),
    ADD COLUMN reserve_decision_until TIMESTAMPTZ,
    ADD CONSTRAINT ck_auctions_reserve CHECK (reserve_price IS NULL OR reserve_price >= starting_price),
    -- EN: A buy-now price below the reserve would sell for less than the seller said they would accept.
    -- VI: Giá mua ngay thấp hơn giá sàn sẽ bán rẻ hơn mức người bán nói họ chấp nhận.
    ADD CONSTRAINT ck_auctions_buy_now CHECK (buy_now_price IS NULL OR (buy_now_price > starting_price
        AND (reserve_price IS NULL OR buy_now_price >= reserve_price))),
    ADD CONSTRAINT ck_auctions_reserve_decision
        CHECK (reserve_decision IS NULL OR reserve_decision IN ('PENDING', 'ACCEPTED', 'DECLINED', 'LAPSED'));

CREATE INDEX ix_auctions_reserve_pending ON auctions (reserve_decision_until) WHERE reserve_decision = 'PENDING';
