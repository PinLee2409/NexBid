-- EN: Public questions on a lot, each answered once by its seller. Everyone sees them, with the asker masked as in the
--     bid history. Deleting a draft lot takes its questions with it.
-- VI: Câu hỏi công khai trên một lô, mỗi câu được người bán trả lời một lần. Ai cũng xem được, tên người hỏi được che
--     như trong lịch sử trả giá. Xoá một lô nháp thì xoá luôn câu hỏi của nó.
CREATE TABLE lot_questions (
    id          UUID          PRIMARY KEY,
    auction_id  UUID          NOT NULL REFERENCES auctions (id) ON DELETE CASCADE,
    asker_id    UUID          NOT NULL REFERENCES users (id),
    body        VARCHAR(500)  NOT NULL,
    answer      VARCHAR(1000),
    created_at  TIMESTAMPTZ   NOT NULL,
    answered_at TIMESTAMPTZ
);
CREATE INDEX ix_lot_questions_auction ON lot_questions (auction_id, created_at DESC);
CREATE INDEX ix_lot_questions_waiting ON lot_questions (auction_id, asker_id) WHERE answer IS NULL;
