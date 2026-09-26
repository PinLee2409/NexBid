# NEXBID — BACKLOG SAU 40 CHỨC NĂNG

Backend đã xong đủ 40 chức năng của `NexBid_Implementation_Guide_Step_By_Step.md`. File này ghi lại
những phần **spec có nhắc tới nhưng guide không có bước nào làm**, cùng các cải thiện phát hiện được khi
review và load test. Mỗi mục có: spec nói gì, hiện trạng, hướng làm, cách kiểm thử.

Thứ tự gợi ý: 1 → 2 → 3 (người dùng thấy ngay), rồi 4 → 5 (chất lượng vận hành), phần còn lại khi rảnh.

---

## 1. Kênh WebSocket riêng cho từng user (thông báo realtime)

- **Spec:** §18 — "Frontend có Bell Icon và WebSocket notification channel."
- **Hiện trạng:** Thông báo được lưu DB và đọc qua `GET /api/notifications`. Kênh WebSocket hiện chỉ có
  `/topic/auctions/{id}` (công khai, ai cũng subscribe được); chưa có kênh riêng theo user. Frontend tạm
  poll `GET /api/notifications` mỗi 30 giây (`notification-service.ts`), nên chuông có thể trễ tới 30 giây.
- **Hướng làm:**
  - Xác thực JWT ở frame STOMP `CONNECT` (header `Authorization`) trong `ChannelInterceptor` của
    `WebSocketConfig`, gắn `Principal` vào session.
  - Dùng user destination của Spring: server gửi `convertAndSendToUser(userId, "/queue/notifications", …)`,
    client subscribe `/user/queue/notifications`. Chặn subscribe `/user/**` khi chưa đăng nhập.
  - Gửi sau khi `NotificationService.notify` ghi DB thành công (after commit), để chuông không báo một
    thông báo rồi bị rollback.
- **Kiểm thử:** hai client STOMP đăng nhập bằng hai user; OUTBID của A chỉ tới A. Client không token
  subscribe `/user/queue/notifications` bị từ chối.

## 2. Các loại thông báo còn thiếu

- **Spec:** §18 liệt kê `AUCTION_EXTENDED`, `PAYMENT_REQUIRED`, `AUCTION_CANCELLED`.
- **Hiện trạng:** `NotificationType` mới có `OUTBID, AUCTION_WON, AUCTION_LOST, AUCTION_STARTING,
  AUCTION_ENDING, PAYMENT_SUCCESS, PAYMENT_EXPIRED`.
- **Hướng làm:**
  - `PAYMENT_REQUIRED`: lắng nghe `PaymentEvents.Opened` (cần thêm `@Externalized` cho event này) —
    báo người thắng kèm hạn trả tiền. Có thể thay cho `AUCTION_WON` hoặc gửi kèm; cần quyết định.
  - `AUCTION_CANCELLED`: khi phiên chuyển `CANCELLED` (hết hạn thanh toán) — báo người bán.
  - `AUCTION_EXTENDED`: **cần chốt nghiệp vụ trước**: báo ai? (người đang theo dõi lô? người đã trả giá?)
    Gửi mỗi lần gia hạn có thể thành spam khi anti-sniping kích hoạt liên tục.
  - Thêm các loại mới vào type thông báo phía frontend (`frontend/src/types/index.ts`) cho khớp.
- **Kiểm thử:** như `NotificationApiTest` — mỗi loại một test, kiểm cả "không gửi nhầm người".

## 3. Trạng thái Order `PROCESSING` / `COMPLETED` và Payment `REFUNDED`

- **Spec:** §5.3 Order: `PENDING_PAYMENT, PAID, PROCESSING, COMPLETED, CANCELLED`. §5.2 Payment có `REFUNDED`.
- **Hiện trạng:** `OrderStatus` mới có `PENDING_PAYMENT, PAID, CANCELLED`; `PaymentStatus` chưa có `REFUNDED`.
  Chưa có luồng giao hàng hay hoàn tiền nào dùng tới các trạng thái này.
- **Hướng làm:** cần chốt nghiệp vụ: ai chuyển `PAID → PROCESSING → COMPLETED` (người bán xác nhận gửi
  hàng, người mua xác nhận đã nhận?), khi nào hoàn tiền. Kèm theo đó là **màn hình đơn hàng cho người bán**
  (hiện chỉ người mua xem được đơn của mình qua `/api/users/me/orders`).
- **Kiểm thử:** mỗi bước chuyển trạng thái chỉ đúng người được làm; chuyển sai thứ tự bị từ chối.

## 4. Hiệu năng: bước kiểm tra "tài khoản bị khoá" trong JWT filter

- **Nguồn:** load test (`docs/load-test/README.md`) — ở 1000 user, p95 của bid tăng lên 200 ms và 8/10
  connection DB bận cùng lúc.
- **Hiện trạng:** `JwtAuthenticationFilter` gọi `users.findById` ở **mọi** request có token (thêm ở chức năng
  35 để chặn ngay user bị khoá). Pool Hikari mặc định 10 connection.
- **Hướng làm (đo lại sau mỗi bước):**
  - Cache trạng thái user ngắn hạn (vài giây, trong bộ nhớ hoặc Redis), xoá cache khi admin khoá/mở khoá.
  - Thử tăng `spring.datasource.hikari.maximum-pool-size` (ví dụ 20) và so sánh.
- **Kiểm thử:** chạy lại `bash docs/load-test/run.sh`, so bảng kết quả. Test hiện có về khoá tài khoản vẫn
  phải xanh (user bị khoá mất quyền ngay, hoặc sau đúng thời gian cache đã chấp nhận).

## 5. Observability (Prometheus / Grafana / OpenTelemetry)

- **Spec:** §34 — "Version nâng cao" (tuỳ chọn). Metrics gợi ý: `bid_requests_total`, `bid_success_total`,
  `bid_failed_total`, `active_auctions`, `websocket_connections`, `bid_latency`, `payment_success_total`.
  §35 gợi ý thêm `prometheus`, `grafana` vào Docker Compose.
- **Hiện trạng:** Actuator chỉ mở `/actuator/health`.
- **Hướng làm:** thêm `micrometer-registry-prometheus`, mở `/actuator/prometheus` (chỉ trong mạng nội bộ
  compose), đếm các metric trên bằng `MeterRegistry`; thêm service `prometheus` + `grafana` vào
  `docker-compose.yml` dưới một profile riêng, kèm một dashboard JSON.

## 6. Analytics Consumer

- **Spec:** §19 — consumer gồm Notification, Analytics, Audit.
- **Hiện trạng:** chỉ có Notification Consumer. Audit được ghi **ngay trong transaction** (chắc chắn hơn
  consumer Kafka vì không bao giờ lệch với dữ liệu), nên không cần Audit Consumer.
- **Hướng làm:** một consumer group mới (`nexbid-analytics`) đọc `nexbid.auctions`/`nexbid.payments`, dùng lại
  `ConsumedEvents` để chống xử lý trùng, tổng hợp số liệu (lượt bid theo giờ, doanh thu, tỉ lệ phiên có
  người thắng) vào bảng riêng hoặc làm nguồn cho mục 5.

## 7. Kafka: Dead Letter Topic

- **Nguồn:** guide (giai đoạn 5: "Retry, DLQ").
- **Hiện trạng:** event không đọc được, hoặc bị database từ chối hẳn (vi phạm ràng buộc, ví dụ trỏ tới user
  không tồn tại), bị ghi log ERROR rồi bỏ qua; lỗi khác thì retry vô hạn
  (`KafkaConfig`). Event bị bỏ qua chỉ còn trong log.
- **Hướng làm:** dùng `DeadLetterPublishingRecoverer` gửi event hỏng sang `nexbid.auctions.DLT` /
  `nexbid.payments.DLT` thay vì chỉ log, để xem lại và phát lại được.
- **Ghi chú vận hành:** nếu app bị tắt đột ngột (kill), consumer group phải chờ khoảng 45 giây
  (`session.timeout.ms`) mới giao lại partition — thông báo tới chậm trong khoảng đó. Tắt app bình thường thì
  không bị.

## 8. Quy tắc nhỏ còn hở

- **Admin tự khoá mình / khoá admin khác:** `PATCH /api/admin/users/{id}/block` chưa chặn. Nên từ chối
  khoá chính mình và (tuỳ quyết định) khoá tài khoản ADMIN khác.
- **Khoá ảnh sản phẩm khi lô đã duyệt:** người bán vẫn đổi được ảnh của sản phẩm đang đấu giá
  (`ProductImageService`). Nên chặn thêm/xoá/đổi ảnh bìa khi sản phẩm ở trạng thái `IN_AUCTION`, giống như
  đã chặn sửa thông tin sản phẩm.

## 9. Số người đang xem lô (viewer count)

- **Spec:** §20.3 "Online Viewer Count", key ví dụ `auction:viewers:123`; roadmap Phase 5 — Redis.
- **Hiện trạng:** backend chưa đếm. Frontend đã có chỗ hiển thị (`viewerCount` ở `bid-terminal.tsx`,
  `opening-stage.tsx`) nhưng ẩn đi khi không có số.
- **Hướng làm:** khi một session STOMP subscribe `/topic/auctions/{id}` thì `SADD auction:viewers:{id}
  <sessionId>`, unsubscribe/disconnect thì `SREM`; phát `VIEWER_COUNT` lên topic của lô (gộp lại, ví dụ tối
  đa 1 lần/giây). Đặt TTL cho key để session chết bất thường không bị đếm mãi. Trả `viewerCount` trong
  `GET /api/auctions/{id}` cho lần render đầu.
- **Kiểm thử:** hai client subscribe cùng lô → số là 2; một client ngắt kết nối → số về 1.

## 10. Dọn dẹp môi trường dev

- DB dev (`nexbid` trên cổng 55432) còn user và lô tạo ra khi kiểm thử các chức năng trước. Nếu muốn sạch:
  `docker compose -f docker/compose.yaml down -v` rồi `up -d` (mất **toàn bộ** dữ liệu dev, Flyway tạo lại
  schema khi backend khởi động).
