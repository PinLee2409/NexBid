-- EN: Refunds are bank transfers an admin makes by hand, like payouts, so buyers keep a bank account too: the seller
--     table becomes everyone's. VNPay's refund API is no longer called, and its reply columns give way to the transfer:
--     its bank reference and the account the money went to, copied at the time.
-- VI: Hoàn tiền là chuyển khoản admin tự làm, như chi trả, nên người mua cũng có tài khoản ngân hàng: bảng của người
--     bán thành bảng chung. Không gọi API hoàn tiền của VNPay nữa, và các cột lưu câu trả lời của nó nhường chỗ cho lần
--     chuyển khoản: mã giao dịch ngân hàng và tài khoản đã nhận tiền, sao chép tại thời điểm đó.
ALTER TABLE seller_bank_accounts RENAME TO bank_accounts;

ALTER TABLE payment_refunds DROP CONSTRAINT ck_payment_refunds_method;
ALTER TABLE payment_refunds DROP COLUMN method;
ALTER TABLE payment_refunds DROP COLUMN request_id;
ALTER TABLE payment_refunds DROP COLUMN response_code;
ALTER TABLE payment_refunds DROP COLUMN transaction_no;
-- EN: Null only on refunds recorded before this change. / VI: Chỉ null ở các lần hoàn ghi trước thay đổi này.
ALTER TABLE payment_refunds ADD COLUMN reference VARCHAR(80);
ALTER TABLE payment_refunds ADD COLUMN bank_name VARCHAR(120);
ALTER TABLE payment_refunds ADD COLUMN account_number VARCHAR(40);
ALTER TABLE payment_refunds ADD COLUMN account_holder VARCHAR(120);
