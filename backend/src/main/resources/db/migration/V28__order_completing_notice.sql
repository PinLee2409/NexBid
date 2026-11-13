-- EN: The buyer is reminded once that a shipped order is about to complete on its own, so it joins the
--     once-per-lot index: the scheduler may look at the same order on every tick until it completes.
-- VI: Người mua được nhắc một lần rằng đơn đã gửi sắp tự hoàn tất, nên loại này vào index một-lần-mỗi-lô:
--     scheduler có thể nhìn thấy cùng một đơn ở mỗi nhịp cho tới khi nó hoàn tất.
DROP INDEX ux_notifications_once_per_lot;
CREATE UNIQUE INDEX ux_notifications_once_per_lot
    ON notifications (user_id, auction_id, type)
    WHERE type IN ('AUCTION_STARTING', 'AUCTION_ENDING', 'AUCTION_WON', 'AUCTION_LOST', 'PAYMENT_REQUIRED',
                   'ORDER_COMPLETING');
