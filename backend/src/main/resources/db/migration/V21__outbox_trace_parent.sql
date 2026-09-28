-- EN: The trace an event was raised in (W3C traceparent, 55 characters), so the relay can continue it when it sends
--     the event to Kafka later, on another thread. Empty for events raised outside any trace, such as a scheduler tick.
-- VI: Trace mà sự kiện được phát ra trong đó (W3C traceparent, 55 ký tự), để relay nối tiếp nó khi gửi sự kiện sang
--     Kafka sau này, trên một luồng khác. Để trống với sự kiện phát ra ngoài mọi trace, như một nhịp scheduler.
ALTER TABLE outbox_events ADD COLUMN trace_parent VARCHAR(55);
