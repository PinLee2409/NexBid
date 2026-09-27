-- EN: The Analytics Consumer's totals (spec §19), one row per UTC hour. Written only by the consumer, which
--     adds to a row rather than recounting, so each event must be counted once (consumed_events sees to it).
-- VI: Số liệu tổng của Analytics Consumer (spec §19), mỗi giờ UTC một dòng. Chỉ consumer ghi vào, bằng cách
--     cộng dồn chứ không đếm lại, nên mỗi sự kiện chỉ được tính một lần (consumed_events đảm bảo điều đó).
CREATE TABLE analytics_hourly (
    hour           TIMESTAMPTZ   PRIMARY KEY,
    bids           INTEGER       NOT NULL DEFAULT 0,
    auctions_ended INTEGER       NOT NULL DEFAULT 0,
    auctions_sold  INTEGER       NOT NULL DEFAULT 0,
    payments       INTEGER       NOT NULL DEFAULT 0,
    revenue        NUMERIC(15,2) NOT NULL DEFAULT 0
);
