# NexBid: hiện trạng và việc tiếp theo

Cập nhật: 15/11/2026 (đợt 28: quên và đổi mật khẩu, mã QR cho đăng nhập hai bước, đánh giá chi tiết và trang người bán, video và ảnh 360°, chat có "đang gõ" và "đã xem", gợi ý giá khởi điểm, cài đặt như ứng dụng (PWA), bảng kê tháng cho người bán). Lịch sử chi tiết từng đợt nằm trong [NexBid_Backlog.md](NexBid_Backlog.md).

## 1. Web đã tới đâu

### Người mua
- Duyệt, tìm kiếm (không dấu: "dong ho" ra "Đồng hồ"), lọc lô theo danh mục; cuối trang lô có lô tương tự và lô đã xem gần đây; trang lô cập nhật giá trực tiếp qua WebSocket, đồng hồ đếm ngược, chống bắn tỉa (gia hạn phút cuối).
- Trả giá, tự động trả giá (auto-bid), mua ngay, theo dõi lô.
- Hỏi đáp công khai trên trang lô: người mua hỏi, người bán trả lời ngay dưới câu hỏi; mỗi người tối đa 3 câu đang chờ trên một lô.
- Lưu tìm kiếm và theo dõi người bán: có lô mới khớp thì nhận thông báo ngay, và email tổng hợp mỗi ngày lúc 8:00.
- Nhắn tin với người bán về đơn hàng; số điện thoại, email, link bị chặn để giữ giao dịch trên sàn.
- Phí ship trên trang lô (GHN, theo địa chỉ của người xem); chọn tỉnh, quận, phường từ danh sách của GHN.
- Phí ship thu cùng tiền hàng: trước khi sang VNPay thấy tiền hàng, phí ship (GHN hoặc phí cố định 50.000₫) và tổng; phí chốt theo địa chỉ, đổi địa chỉ thì báo lại.
- Giá sàn ẩn: người mua chỉ thấy "chưa đạt / đã đạt giá sàn".
- Thanh toán qua VNPay (bản demo đã bỏ). Phải có địa chỉ nhận hàng trước khi thanh toán.
- Đơn hàng: theo dõi mã vận đơn và trạng thái kiện từ GHN, xác nhận đã nhận, đánh giá người bán (1–5 sao).
- Khiếu nại đơn hàng (chưa nhận, sai mô tả, hư hỏng, khác).
- Cơ hội thứ hai: khi người thắng không trả tiền, người trả giá kế tiếp được mời mua.
- Đăng nhập hai bước bằng ứng dụng xác thực (kèm 8 mã khôi phục) và đăng nhập bằng Google.
- Thông báo trong app và email song ngữ; tắt được email bị vượt giá, có link huỷ đăng ký một chạm.
- Giao diện tiếng Việt và tiếng Anh, giá theo ngôn ngữ (`18.500.000 ₫` / `₫18,500,000`), giao diện sáng và tối, dùng tốt trên điện thoại.

### Người bán
- Gửi yêu cầu bán hàng, admin duyệt.
- Đăng sản phẩm kèm ảnh và kích thước đóng gói (để báo phí ship), tạo phiên đấu giá (giá khởi điểm, bước giá, giá sàn, giá mua ngay).
- Quyết định bán khi lô đóng dưới giá sàn (trong 24 giờ).
- Đánh giá người mua sau mỗi đơn hoàn tất, xem hồ sơ người mua cạnh đơn bán.
- Đơn bán: xem địa chỉ giao, chọn đơn vị vận chuyển và nhập mã vận đơn; mã GHN được kiểm với GHN, sai thì báo ngay.
- "Gửi bằng GHN": NexBid tự tạo vận đơn GHN bằng tài khoản của sàn, có mã và phiếu in ngay; sàn trả tiền GHN từ phí ship người mua đã trả.
- Nhập nhiều lô từ file CSV kèm ảnh (tối đa 50 dòng), mỗi dòng thành lô nháp; có file mẫu.
- Lên lịch nhiều lô nháp một lúc: cùng giờ mở, giờ đóng, và gửi duyệt ngay hoặc hẹn giờ.
- Lô gồm nhiều món (bán theo bộ): sản phẩm liệt kê các món và số lượng; trang lô hiện danh sách.
- Người bán có thể chọn miễn phí vận chuyển; người mua chỉ trả tiền hàng.
- Huỷ đơn GHN trước khi lấy hàng để đặt lại; người mua đổi địa chỉ nhận của đơn chưa gửi.
- Lịch đấu giá (/calendar) theo ngày; theo dõi lô là đặt nhắc.
- Thông báo đẩy trên trình duyệt (Web Push), bật trong trang Hồ sơ.
- Biên nhận in được (lưu PDF) cho người mua và bảng kê cho người bán.
- Quên mật khẩu (link qua email, 30 phút, một lần) và đổi mật khẩu trong trang Hồ sơ.
- Mã QR khi bật đăng nhập hai bước.
- Đánh giá người bán theo đóng gói, đúng mô tả, tốc độ; trang công khai của người bán (/sellers/{id}).
- Video ngắn và chế độ xem 360° cho sản phẩm.
- Tin nhắn đơn hàng có "đang gõ…" và "đã xem".
- Gợi ý giá khởi điểm khi đăng lô, theo giá bán của các lô tương tự.
- Cài NexBid lên màn hình chính (PWA), có trang offline.
- Bảng kê tháng cho người bán (/seller/statements), tải CSV hoặc in PDF.
- Đăng lại lô không bán được (sao chép giá và cài đặt), trang Thống kê: người xem, theo dõi, trả giá từng lô, tỉ lệ bán được.
- Thu nhập: số tiền chờ chi trả, đã nhận, phí sàn 5% (chỉ tính trên tiền hàng), phí ship người mua đã trả được cộng lại, tài khoản ngân hàng nhận tiền.

### Admin (trang đăng nhập riêng `/admin/login`)
- Duyệt lô, quản lý danh mục, người dùng (khoá / mở khoá, xác minh người bán), yêu cầu bán hàng.
- Đơn hàng: hoàn tiền bằng chuyển khoản thủ công vào tài khoản người mua đã cho, kèm mã giao dịch.
- Khiếu nại: hoàn tiền cho người mua hoặc giữ giao dịch, kèm ghi chú; đọc tin nhắn giữa hai bên (mỗi lần đọc ghi vào nhật ký).
- Voucher phí ship do admin phát (/admin/promos); ba giao dịch đầu của người bán mới không tính phí sàn.
- Biểu đồ phí sàn theo tháng (Tài chính) và lô được xem nhiều nhất (Thống kê người bán).
- Tài chính: đối soát VNPay theo ngày (đã thu, áp vào lô, phải trả lại, đã hoàn, đã chi), doanh thu theo tháng, danh mục, người bán; xuất CSV mở bằng Excel.
- Chi trả cho người bán: danh sách người bán đang chờ tiền, ghi nhận chuyển khoản kèm mã giao dịch.
- Khoản VNPay thu thừa (trễ hạn hoặc trả hai lần): hiện ở trang Đơn hàng để chuyển khoản trả lại.
- Nghi vấn tự trả giá: cặp người bán–người trả giá có dấu hiệu (cùng trình duyệt hoặc cùng mạng, gần như chỉ trả giá một người bán, không bao giờ thắng, hay dừng ngay dưới người thắng, tài khoản mới); admin bỏ nghi vấn hoặc khoá người trả giá.
- Báo cáo lô vi phạm: bỏ qua hoặc gỡ lô; trang Email để gửi lại email thất bại.
- Nhật ký hệ thống, thống kê hoạt động.

### Hệ thống
- Backend Spring Boot 4 chia module (Spring Modulith), Postgres, Redis, Kafka (outbox), OpenTelemetry.
- Đơn đã gửi tự hoàn tất sau 7 ngày nếu người mua không xác nhận và không khiếu nại; gửi bằng GHN thì 3 ngày sau khi GHN báo đã giao.
- Tích hợp GHN (sandbox): phí ship, trạng thái vận đơn, danh sách tỉnh/quận/phường; webhook cần địa chỉ công khai, chạy ở máy thì hỏi GHN 30 phút một lần.
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
- [x] **Gửi mẫu 6 email mới** (14/11/2026): đã gửi qua Brevo tới Gmail của chủ dự án, cùng 2 email hỏi đáp.
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
- Trang `/payments/vnpay-return` không cần đăng nhập (từ đợt 18).
- Giao dịch test 15736604 (2.000.000 ₫) vẫn ở `PAID` trên database dev; tiền sandbox không phải tiền thật nên không cần
  hoàn trong trang VNPay.

## 4. Việc cần chủ dự án chuẩn bị

- [ ] Đăng nhập Google: tạo OAuth client (loại Web application) trên Google Cloud Console, thêm `http://localhost:3000` vào Authorized JavaScript origins, rồi đặt `NEXBID_GOOGLE_CLIENT_ID` trong `backend/.env`. Chưa đặt thì nút Google không hiện.

- [x] VNPay kích hoạt lại mã website sandbox `VVK6HZ1B`.
- [x] Key Brevo: đã có, đã gửi thử thành công (mail vào thư rác vì gửi bằng địa chỉ Gmail).
- [x] GHN sandbox: cửa hàng 239091 trên `5sao.ghn.dev`, đã có địa chỉ lấy hàng; Token, ShopId và webhook secret đã nằm trong `backend/.env`.
- [ ] Mua domain, rồi deploy staging bằng `deploy/staging/`. Có domain thì:
  - IPN của VNPay gọi về được thật (không cần ngrok);
  - email gửi bằng `no-reply@domain` đã xác thực SPF/DKIM, không vào thư rác;
  - logo trong email tự hiện.

## 5. Tính năng đề xuất tiếp theo

### Nên làm trước

1. **Đặt cọc trước khi đấu giá lô giá trị cao.** Với lô từ một mức giá cấu hình được (ví dụ 20 triệu), người mua phải cọc qua VNPay khoảng 5–10% mới được trả giá. Thắng mà không trả tiền thì mất cọc; thua thì được hoàn cọc tự động. Giải quyết vấn đề lớn nhất của sàn đấu giá: người thắng bỏ không trả tiền.
   *Tạm dừng (14/11/2026): giữ số dư (ví) cho người dùng cần giấy phép của Ngân hàng Nhà nước, nên không làm ví. Nếu làm, thu cọc qua VNPay và hoàn bằng chuyển khoản tay như hoàn tiền hiện nay. Đợt 18 đã có giải pháp nhẹ hơn: người bỏ không trả 2 lần không được trả giá từ 20 triệu.*

