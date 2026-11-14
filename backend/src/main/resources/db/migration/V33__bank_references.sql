-- EN: Each bank reference an admin records (refund, extra charge sent back, payout) may be used once, across all three.
--     Existing ones are carried over; where old data already repeats one, the first is kept and the rest left as they are.
-- VI: Mỗi mã giao dịch ngân hàng admin ghi (hoàn tiền, trả lại khoản thu thừa, chi trả) chỉ dùng một lần, trên cả ba loại.
--     Mã cũ được chép sang; dữ liệu cũ nào đã trùng thì giữ mã đầu tiên, phần còn lại để nguyên.
CREATE TABLE bank_references (
    reference   VARCHAR(80)  PRIMARY KEY,
    purpose     VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

INSERT INTO bank_references (reference, purpose, created_at)
SELECT upper(regexp_replace(reference, '\s+', '', 'g')), 'REFUND', created_at
FROM payment_refunds WHERE reference IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO bank_references (reference, purpose, created_at)
SELECT upper(regexp_replace(reference, '\s+', '', 'g')), 'PAYOUT', created_at
FROM seller_payouts WHERE reference IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO bank_references (reference, purpose, created_at)
SELECT upper(regexp_replace(reference, '\s+', '', 'g')), 'STRAY', refunded_at
FROM stray_charges WHERE reference IS NOT NULL
ON CONFLICT DO NOTHING;
