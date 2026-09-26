-- EN: Spec §18's remaining types. The payment reminder and the cancelled sale also happen once per person
--     per lot, so they join the once-per-lot index.
-- VI: Các loại còn lại của spec §18. Lời nhắc thanh toán và giao dịch bị huỷ cũng chỉ xảy ra một lần cho mỗi
--     người trên mỗi lô, nên được thêm vào index một-lần-mỗi-lô.
DROP INDEX ux_notifications_once_per_lot;
CREATE UNIQUE INDEX ux_notifications_once_per_lot
    ON notifications (user_id, auction_id, type)
    WHERE type IN ('AUCTION_STARTING', 'AUCTION_ENDING', 'AUCTION_WON', 'AUCTION_LOST',
                   'PAYMENT_REQUIRED', 'AUCTION_CANCELLED');

-- EN: An extension is news again only once the last one was read, so a lot extended five times in its
--     final minute rings each person once.
-- VI: Gia hạn chỉ là tin mới khi thông báo gia hạn trước đã được đọc, nên một lô bị gia hạn năm lần trong
--     phút cuối chỉ báo mỗi người một lần.
CREATE UNIQUE INDEX ux_notifications_extended_unread
    ON notifications (user_id, auction_id)
    WHERE type = 'AUCTION_EXTENDED' AND NOT is_read;
