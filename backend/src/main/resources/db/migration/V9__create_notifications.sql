-- EN: Notifications (guide §29, spec §18, columns from spec §23). auction_id is added so a notice can
--     link to its lot — the "BID AGAIN" and "PAY NOW" buttons in spec §38 need somewhere to go.
-- VI: Thông báo (guide §29, spec §18, các cột theo spec §23). Thêm auction_id để thông báo trỏ được về
--     lô của nó — nút "BID AGAIN" và "PAY NOW" ở spec §38 cần biết đi tới đâu.

CREATE TABLE notifications (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(32)  NOT NULL,
    title      VARCHAR(160) NOT NULL,
    message    VARCHAR(500) NOT NULL,
    auction_id UUID         REFERENCES auctions (id) ON DELETE CASCADE,
    is_read    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL
);

-- EN: The bell reads a user's newest first, and counts the unread ones on every page load.
-- VI: Cái chuông đọc thông báo mới nhất của một user, và đếm số chưa đọc mỗi lần tải trang.
CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE NOT is_read;

-- EN: Deleting a draft auction cascades here. / VI: Xoá một phiên nháp sẽ lan xuống bảng này.
CREATE INDEX ix_notifications_auction ON notifications (auction_id);

-- EN: These happen once per person per lot. Outbid is left out on purpose — being outbid twice is two
--     events. The index makes "once" hold even if a job runs twice or two instances overlap.
-- VI: Những loại này chỉ xảy ra một lần cho mỗi người trên mỗi lô. Cố ý bỏ OUTBID ra — bị vượt giá hai lần
--     là hai sự kiện. Index giữ đúng "một lần" kể cả khi job chạy hai lần hay hai instance chồng nhau.
CREATE UNIQUE INDEX ux_notifications_once_per_lot
    ON notifications (user_id, auction_id, type)
    WHERE type IN ('AUCTION_STARTING', 'AUCTION_ENDING', 'AUCTION_WON', 'AUCTION_LOST');
