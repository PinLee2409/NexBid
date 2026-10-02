-- EN: Requests to become a seller (spec §7.1, "User có thể đăng ký Seller sau này"). An admin approves or rejects
--     each one; a rejected applicant may apply again, so a person can have many rows but only one waiting.
-- VI: Yêu cầu trở thành người bán (spec §7.1, "User có thể đăng ký Seller sau này"). Admin duyệt hoặc từ chối từng
--     yêu cầu; người bị từ chối được gửi lại, nên một người có thể có nhiều dòng nhưng chỉ một dòng đang chờ.
CREATE TABLE seller_applications (
    id               UUID         PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- EN: What the applicant plans to sell, in their own words. / VI: Người nộp định bán gì, theo lời của họ.
    note             VARCHAR(1000) NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    rejection_reason VARCHAR(500),
    created_at       TIMESTAMPTZ  NOT NULL,
    decided_at       TIMESTAMPTZ,
    decided_by       UUID         REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_seller_applications_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

-- EN: One waiting request per person, even if two tabs send at once. / VI: Mỗi người một yêu cầu đang chờ, kể cả khi hai tab gửi cùng lúc.
CREATE UNIQUE INDEX ux_seller_applications_one_pending ON seller_applications (user_id) WHERE status = 'PENDING';
CREATE INDEX ix_seller_applications_user ON seller_applications (user_id, created_at DESC);
CREATE INDEX ix_seller_applications_status ON seller_applications (status, created_at);
