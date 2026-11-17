-- EN: A draft can be sent for approval at a set time; the auction scheduler sends it then.
-- VI: Một bản nháp có thể được hẹn giờ gửi duyệt; bộ lập lịch phiên gửi nó đúng lúc đó.
ALTER TABLE auctions ADD COLUMN submit_at TIMESTAMPTZ;
CREATE INDEX ix_auctions_submit_at ON auctions (submit_at) WHERE submit_at IS NOT NULL;
