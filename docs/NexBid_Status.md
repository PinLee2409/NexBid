# NexBid: hiện trạng và việc tiếp theo

Cập nhật: 14/11/2026. Lịch sử chi tiết từng đợt nằm trong [NexBid_Backlog.md](NexBid_Backlog.md).

## 1. Web đã tới đâu

### Người mua
- Duyệt, tìm kiếm, lọc lô theo danh mục; trang lô cập nhật giá trực tiếp qua WebSocket, đồng hồ đếm ngược, chống bắn tỉa (gia hạn phút cuối).
- Trả giá, tự động trả giá (auto-bid), mua ngay, theo dõi lô.
- Hỏi đáp công khai trên trang lô: người mua hỏi, người bán trả lời ngay dưới câu hỏi; mỗi người tối đa 3 câu đang chờ trên một lô.
- Giá sàn ẩn: người mua chỉ thấy "chưa đạt / đã đạt giá sàn".
- Thanh toán qua VNPay (bản demo đã bỏ). Phải có địa chỉ nhận hàng trước khi thanh toán.
- Đơn hàng: theo dõi mã vận đơn, xác nhận đã nhận, đánh giá người bán (1–5 sao).
- Khiếu nại đơn hàng (chưa nhận, sai mô tả, hư hỏng, khác).
- Cơ hội thứ hai: khi người thắng không trả tiền, người trả giá kế tiếp được mời mua.
- Thông báo trong app và email song ngữ; tắt được email bị vượt giá, có link huỷ đăng ký một chạm.
- Giao diện tiếng Việt và tiếng Anh, giá theo ngôn ngữ (`18.500.000 ₫` / `₫18,500,000`), giao diện sáng và tối, dùng tốt trên điện thoại.

### Người bán
- Gửi yêu cầu bán hàng, admin duyệt.
- Đăng sản phẩm kèm ảnh, tạo phiên đấu giá (giá khởi điểm, bước giá, giá sàn, giá mua ngay).
- Quyết định bán khi lô đóng dưới giá sàn (trong 24 giờ).
- Đơn bán: xem địa chỉ giao, nhập đơn vị vận chuyển và mã vận đơn.
- Thu nhập: số tiền chờ chi trả, đã nhận, phí sàn 5%, tài khoản ngân hàng nhận tiền.

### Admin (trang đăng nhập riêng `/admin/login`)
- Duyệt lô, quản lý danh mục, người dùng (khoá / mở khoá), yêu cầu bán hàng.
- Đơn hàng: hoàn tiền bằng chuyển khoản thủ công vào tài khoản người mua đã cho, kèm mã giao dịch.
- Khiếu nại: hoàn tiền cho người mua hoặc giữ giao dịch, kèm ghi chú.
- Chi trả cho người bán: danh sách người bán đang chờ tiền, ghi nhận chuyển khoản kèm mã giao dịch.
- Nhật ký hệ thống, thống kê hoạt động.

### Hệ thống
- Backend Spring Boot 4 chia module (Spring Modulith), Postgres, Redis, Kafka (outbox), OpenTelemetry.
- Đơn đã gửi tự hoàn tất sau 7 ngày nếu người mua không xác nhận và không khiếu nại.
- 26 loại email song ngữ qua Brevo, có logo, tự gửi lại khi lỗi.
- CI: test backend (độ phủ JaCoCo), kiểm tra frontend, test E2E Playwright.
- File deploy staging với HTTPS tự động (Caddy) trong `deploy/staging/`, chưa deploy thật.

## 2. Đợt 14 (sau thanh toán) và đợt 15 (hỏi đáp)

Đã commit và push lên `dev` (14/11/2026).

- [x] **Chạy lại toàn bộ test backend** (`mvn verify`): 449/449 test qua, đạt ngưỡng độ phủ (95.4% dòng, 81.5% nhánh).
- [x] **Kiểm tra trên trình duyệt** trên stack Docker riêng (project `nexbid-e2e`, cổng 18080/13000, Postgres, Redis,
  Kafka riêng), qua hai test E2E mới: người mua gửi khiếu nại → admin giữ giao dịch kèm ghi chú → người mua thấy kết
  quả và thông báo; người bán nhập tài khoản, xem thu nhập → admin ghi nhận chuyển khoản → người bán thấy mã giao dịch và
  thông báo. Phát hiện và sửa: trạng thái đơn `DISPUTED` thiếu nhãn ở `enums.orderStatus` (lỗi `MISSING_MESSAGE`).
  Nhánh admin **chấp nhận** khiếu nại (hoàn tiền) mới được test ở API, chưa bấm thử trên giao diện.
- [ ] **Gửi mẫu 6 email mới** để duyệt giao diện: đã dựng bản xem trước HTML (khiếu nại mở, khiếu nại bị bác cho người
  mua và người bán, đã hoàn tiền cho người mua (gửi người bán), đã chuyển tiền, đơn sắp tự hoàn tất); chưa gửi thật qua Brevo.
- [x] **Ghi nhật ký hệ thống**: mở khiếu nại, hoàn tiền / bác khiếu nại (kèm ghi chú), ghi nhận chi trả, đổi tài khoản
  nhận tiền (chỉ tên ngân hàng và 4 số cuối). Đơn hoàn tất sau khi bác khiếu nại giờ ghi `DISPUTED → COMPLETED`.
- [x] **Thông báo trong app**: `DISPUTE_OPENED` (người bán), `DISPUTE_RESOLVED` (người mua), `DISPUTE_SELLER_RESOLVED`
  (người bán), `PAYOUT_SENT` (người bán), `ORDER_COMPLETING` (người mua).
- [x] **Email nhắc người mua** 2 ngày trước khi đơn tự hoàn tất (`ORDER_COMPLETING`, một lần mỗi đơn, migration V28).
- [x] **Test E2E** cho luồng khiếu nại và chi trả (`frontend/e2e/after-sale.spec.ts`); cả bộ E2E 43/43.
- [x] Cập nhật Backlog và README.
- [x] Commit và push.

## 3. VNPay: chỉ dùng để thanh toán; hoàn tiền là chuyển khoản thủ công

Khoá sandbox (`VVK6HZ1B`) đã được VNPay kích hoạt (14/11/2026). Local đặt khoá trong `backend/.env` (git bỏ qua),
mục `nexbid-backend` của `.claude/launch.json` nạp file này; chạy bằng IntelliJ thì phải thêm 2 biến vào run configuration.

- **Thanh toán thật qua sandbox: chạy.** Backend tạo link, VNPay nhận chữ ký, trả bằng thẻ NCB test
  (`9704198526191432198`, `NGUYEN VAN A`, `07/15`, OTP `123456`), quay về `/payments/vnpay-return`: khoản thành
  `SUCCESS`, đơn thành `PAID` (giao dịch VNPay 15736604).
- **Hoàn tiền không gọi VNPay nữa** (14/11/2026). API `merchant_webapi` của sandbox trả `99 Unexpected error` cho mọi
  yêu cầu, kể cả yêu cầu sai chữ ký, nên đã bỏ hẳn. Giờ:
  1. Người mua lưu **tài khoản nhận hoàn tiền** trong Hồ sơ, hoặc nhập ngay trong hộp khiếu nại nếu chưa có.
  2. Admin bấm **Hoàn tiền** (trang Đơn hàng hoặc Khiếu nại): thấy số tiền và tài khoản kèm nút chép, tự chuyển khoản,
     rồi nhập **mã giao dịch ngân hàng**. Người mua chưa có tài khoản thì nút bị khoá.
  3. Đơn huỷ, khoản thanh toán `REFUNDED`, `payment_refunds` lưu mã giao dịch và bản sao tài khoản; người mua nhận email
     và thông báo kèm mã giao dịch; nhật ký ghi lại.
- **Lưu ý:** trang `/payments/vnpay-return` cần đăng nhập. Nếu người mua hết phiên, xác nhận chỉ chạy sau khi đăng nhập
  lại (link quay về giữ nguyên tham số VNPay). Trên staging thì IPN lo phần này.
- Giao dịch test 15736604 (2.000.000 ₫) vẫn ở `PAID` trên database dev; tiền sandbox không phải tiền thật nên không cần
  hoàn trong trang VNPay.

## 4. Việc cần chủ dự án chuẩn bị

- [x] VNPay kích hoạt lại mã website sandbox `VVK6HZ1B`.
- [x] Key Brevo: đã có, đã gửi thử thành công (mail vào thư rác vì gửi bằng địa chỉ Gmail).
- [ ] Mua domain, rồi deploy staging bằng `deploy/staging/`. Có domain thì:
  - IPN của VNPay gọi về được thật (không cần ngrok);
  - email gửi bằng `no-reply@domain` đã xác thực SPF/DKIM, không vào thư rác;
  - logo trong email tự hiện.

## 5. Tính năng đề xuất tiếp theo

### Nên làm trước

1. **Đặt cọc trước khi đấu giá lô giá trị cao.** Với lô từ một mức giá cấu hình được (ví dụ 20 triệu), người mua phải cọc qua VNPay khoảng 5–10% mới được trả giá. Thắng mà không trả tiền thì mất cọc; thua thì được hoàn cọc tự động. Giải quyết vấn đề lớn nhất của sàn đấu giá: người thắng bỏ không trả tiền.
   *Lưu ý (14/11/2026): hoàn tiền giờ là chuyển khoản tay, nên trước khi làm cần chọn cách hoàn cọc: số dư ví trên NexBid, hoặc chờ VNPay bật API hoàn tiền.*
2. **Lưu tìm kiếm và theo dõi người bán.** Lưu bộ lọc (ví dụ "đồng hồ Seiko dưới 10 triệu") hoặc theo dõi một người bán; có lô mới khớp thì nhận thông báo và email tổng hợp mỗi ngày.

### Làm sau

4. **Tích hợp GHN hoặc GHTK:** tính phí ship theo tỉnh lúc thanh toán, tự lấy trạng thái vận đơn, đơn tự hoàn tất khi đơn vị vận chuyển báo đã giao.
5. **Nhắn tin giữa người mua và người bán** sau khi thắng, để hẹn giao hàng hoặc hỏi về kiện hàng.
6. **Báo cáo lô vi phạm** (hàng giả, mô tả sai), kèm hàng chờ duyệt cho admin.
7. **Người bán đánh giá người mua**, để biết người mua nào hay bỏ đơn.
8. **Huy hiệu người bán đã xác minh** (đã xác minh giấy tờ), hiện trên trang lô.
