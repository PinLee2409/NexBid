# NEXBID — BACKLOG SAU 40 CHỨC NĂNG

Backend đã xong đủ 40 chức năng của `NexBid_Implementation_Guide_Step_By_Step.md`. File này ghi lại
những phần **spec có nhắc tới nhưng guide không có bước nào làm**, cùng các cải thiện phát hiện được khi
review và load test. Mỗi mục có: spec nói gì, hiện trạng, hướng làm, cách kiểm thử.

Thứ tự gợi ý: 1 → 2 (chất lượng vận hành), phần còn lại khi rảnh. Các mục đã làm nằm ở cuối file, kèm các
quyết định nghiệp vụ đã chốt.

---

## 1. Hiệu năng: bước kiểm tra "tài khoản bị khoá" trong JWT filter

- **Nguồn:** load test (`docs/load-test/README.md`) — ở 1000 user, p95 của bid tăng lên 200 ms và 8/10
  connection DB bận cùng lúc.
- **Hiện trạng:** `TokenAuthenticator` (dùng chung cho `JwtAuthenticationFilter` và lúc CONNECT socket) gọi
  `users.findById` ở **mọi** request có token (thêm ở chức năng 35 để chặn ngay user bị khoá). Pool Hikari mặc
  định 10 connection.
- **Hướng làm (đo lại sau mỗi bước):**
  - Cache trạng thái user ngắn hạn (vài giây, trong bộ nhớ hoặc Redis), xoá cache khi admin khoá/mở khoá.
  - Thử tăng `spring.datasource.hikari.maximum-pool-size` (ví dụ 20) và so sánh.
- **Kiểm thử:** chạy lại `bash docs/load-test/run.sh`, so bảng kết quả. Test hiện có về khoá tài khoản vẫn
  phải xanh (user bị khoá mất quyền ngay, hoặc sau đúng thời gian cache đã chấp nhận).

## 2. Observability (Prometheus / Grafana / OpenTelemetry)

- **Spec:** §34 — "Version nâng cao" (tuỳ chọn). Metrics gợi ý: `bid_requests_total`, `bid_success_total`,
  `bid_failed_total`, `active_auctions`, `websocket_connections`, `bid_latency`, `payment_success_total`.
  §35 gợi ý thêm `prometheus`, `grafana` vào Docker Compose.
- **Hiện trạng:** Actuator chỉ mở `/actuator/health`.
- **Hướng làm:** thêm `micrometer-registry-prometheus`, mở `/actuator/prometheus` (chỉ trong mạng nội bộ
  compose), đếm các metric trên bằng `MeterRegistry`; thêm service `prometheus` + `grafana` vào
  `docker-compose.yml` dưới một profile riêng, kèm một dashboard JSON.

## 3. Analytics Consumer

- **Spec:** §19 — consumer gồm Notification, Analytics, Audit.
- **Hiện trạng:** chỉ có Notification Consumer. Audit được ghi **ngay trong transaction** (chắc chắn hơn
  consumer Kafka vì không bao giờ lệch với dữ liệu), nên không cần Audit Consumer.
- **Hướng làm:** một consumer group mới (`nexbid-analytics`) đọc `nexbid.auctions`/`nexbid.payments`, dùng lại
  `ConsumedEvents` để chống xử lý trùng, tổng hợp số liệu (lượt bid theo giờ, doanh thu, tỉ lệ phiên có
  người thắng) vào bảng riêng hoặc làm nguồn cho mục 5.

## 4. Kafka: Dead Letter Topic

- **Nguồn:** guide (giai đoạn 5: "Retry, DLQ").
- **Hiện trạng:** event không đọc được, hoặc bị database từ chối hẳn (vi phạm ràng buộc, ví dụ trỏ tới user
  không tồn tại), bị ghi log ERROR rồi bỏ qua; lỗi khác thì retry vô hạn
  (`KafkaConfig`). Event bị bỏ qua chỉ còn trong log.
- **Hướng làm:** dùng `DeadLetterPublishingRecoverer` gửi event hỏng sang `nexbid.auctions.DLT` /
  `nexbid.payments.DLT` thay vì chỉ log, để xem lại và phát lại được.
- **Ghi chú vận hành:** nếu app bị tắt đột ngột (kill), consumer group phải chờ khoảng 45 giây
  (`session.timeout.ms`) mới giao lại partition — thông báo tới chậm trong khoảng đó. Tắt app bình thường thì
  không bị.

## 5. Quy tắc nhỏ còn hở

- **Admin tự khoá mình / khoá admin khác:** `PATCH /api/admin/users/{id}/block` chưa chặn. Nên từ chối
  khoá chính mình và (tuỳ quyết định) khoá tài khoản ADMIN khác.
- **Khoá ảnh sản phẩm khi lô đã duyệt:** người bán vẫn đổi được ảnh của sản phẩm đang đấu giá
  (`ProductImageService`). Nên chặn thêm/xoá/đổi ảnh bìa khi sản phẩm ở trạng thái `IN_AUCTION`, giống như
  đã chặn sửa thông tin sản phẩm.

## 6. Dọn dẹp môi trường dev

- DB dev (`nexbid` trên cổng 55432) còn user và lô tạo ra khi kiểm thử các chức năng trước. Nếu muốn sạch:
  `docker compose -f docker/compose.yaml down -v` rồi `up -d` (mất **toàn bộ** dữ liệu dev, Flyway tạo lại
  schema khi backend khởi động), xoá ảnh cũ trong `backend/var/images/`, rồi chạy `node docs/demo/seed.mjs`
  để có bộ dữ liệu demo (README → Demo data).

## Đã làm (27/09/2026)

- **Kênh WebSocket riêng cho từng user** (spec §18). Token gửi kèm frame STOMP `CONNECT`
  (`TokenAuthenticator`); người đã đăng nhập subscribe `/user/queue/notifications`, socket ẩn danh bị từ chối.
  Thông báo được đẩy sau commit (`NotificationPusher`). Frontend bỏ poll 30 giây, đọc lại danh sách mỗi lần
  kết nối lại, và hiện toast cho thông báo mới. Test: `NotificationInboxRealtimeTest`.
- **Số người đang xem** (spec §20.3). Mỗi lô một set Redis `nexbid:viewers:{id}`, cập nhật theo
  subscribe/unsubscribe/disconnect; `VIEWER_COUNT` phát trên kênh của lô tối đa mỗi giây một lần;
  `viewerCount` có trong `GET /api/auctions/{id}`. Test: `LotViewersTest`.
- **Các loại thông báo còn thiếu** (spec §18), theo quyết định đã chốt:
  - `AUCTION_EXTENDED`: báo người đã trả giá và người theo dõi lô, trừ người gây gia hạn; không gửi thêm khi
    người đó còn một thông báo gia hạn chưa đọc của cùng lô.
  - `PAYMENT_REQUIRED`: lời nhắc một lần khi còn 12 giờ tới hạn thanh toán mà chưa trả
    (`NEXBID_PAYMENT_REMINDER`); `AUCTION_WON` giữ nguyên.
  - `AUCTION_CANCELLED`: báo người bán và người thắng khi hết hạn thanh toán.
- **Order `PROCESSING`/`COMPLETED`, Payment `REFUNDED`** (spec §5.2, §5.3), theo quyết định đã chốt: người bán
  bấm đã gửi hàng (PAID → PROCESSING), người mua xác nhận đã nhận (PROCESSING → COMPLETED), admin hoàn tiền đơn
  PAID/PROCESSING (Order CANCELLED, Payment REFUNDED). Có trang đơn hàng cho người bán và cho admin; mỗi bước
  được ghi audit log. Test: các test mới trong `OrderApiTest`.
