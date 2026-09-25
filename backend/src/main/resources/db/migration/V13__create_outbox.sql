-- EN: Transactional outbox (guide §36, spec §19). An event is written here in the same transaction as the
--     change it describes, then handed to Kafka after commit and deleted. So a committed bid always gets
--     its event, a rolled-back one never does, and Kafka being down only delays delivery.
-- VI: Transactional outbox (guide §36, spec §19). Sự kiện được ghi vào đây trong cùng transaction với thay
--     đổi mà nó mô tả, rồi giao cho Kafka sau commit và xoá đi. Nhờ vậy bid đã commit luôn có sự kiện, bid
--     bị rollback thì không bao giờ có, và Kafka sập chỉ làm giao chậm lại.

CREATE TABLE outbox_events (
    id          UUID         PRIMARY KEY,
    -- EN: Delivery order. Events about one lot are written under that lot's lock, so this is their real order.
    -- VI: Thứ tự giao. Sự kiện của một lô được ghi khi đang giữ khoá của lô đó, nên đây là thứ tự thật của chúng.
    position    BIGINT       GENERATED ALWAYS AS IDENTITY UNIQUE,
    topic       VARCHAR(100) NOT NULL,
    message_key VARCHAR(100),
    event_type  VARCHAR(100) NOT NULL,
    payload     TEXT         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

-- EN: Kafka delivers at least once, so a consumer may see the same event twice. Recording each event it
--     handled, in the same transaction as its work, makes the second time a no-op.
-- VI: Kafka giao ít nhất một lần, nên consumer có thể thấy cùng một sự kiện hai lần. Ghi lại từng sự kiện
--     đã xử lý, trong cùng transaction với phần việc của nó, khiến lần thứ hai không làm gì cả.
CREATE TABLE consumed_events (
    consumer    VARCHAR(100) NOT NULL,
    event_id    UUID         NOT NULL,
    consumed_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (consumer, event_id)
);

-- EN: Old entries are pruned once Kafka could no longer redeliver them. / VI: Mục cũ được dọn khi Kafka không còn giao lại được nữa.
CREATE INDEX ix_consumed_events_consumed_at ON consumed_events (consumed_at);
