# NEXBID — BACKLOG SAU 40 CHỨC NĂNG

Backend đã xong đủ 40 chức năng của `NexBid_Implementation_Guide_Step_By_Step.md`. File này ghi lại
những phần **spec có nhắc tới nhưng guide không có bước nào làm**, cùng các cải thiện phát hiện được khi
review và load test. Mỗi mục có: spec nói gì, hiện trạng, hướng làm, cách kiểm thử.

Các mục đã làm nằm ở cuối file, kèm các quyết định nghiệp vụ đã chốt.

---

## 1. Dọn dẹp môi trường dev

- DB dev (`nexbid` trên cổng 55432) còn user và lô tạo ra khi kiểm thử các chức năng trước. Nếu muốn sạch:
  `docker compose -f docker/compose.yaml down -v` rồi `up -d` (mất **toàn bộ** dữ liệu dev, Flyway tạo lại
  schema khi backend khởi động), xoá ảnh cũ trong `backend/var/images/`, rồi chạy `node docs/demo/seed.mjs`
  để có bộ dữ liệu demo (README → Demo data).

## Đã làm (27/09/2026) — đợt 1

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

## Đã làm (27/09/2026) — đợt 2

- **Hiệu năng kiểm tra "tài khoản bị khoá":** `TokenAuthenticator` nhớ trạng thái tài khoản 10 giây
  (`NEXBID_STATUS_CACHE_TTL`) và xoá ngay khi admin khoá/mở khoá commit, nên khoá vẫn có hiệu lực ở request kế
  tiếp. Test: `AdminUserListApiTest.aBlockedAccountsTokenStopsWorkingOnTheVeryNextRequest`. Đo lại bằng load test:
  ở 1000 user, p95 của bid từ 200 ms còn 9.3 ms, kết nối DB bận cùng lúc từ 8 còn 4 — không cần tăng pool Hikari.
- **Observability** (spec §34): `micrometer-registry-prometheus`; các metric `bid_requests_total`,
  `bid_success_total`, `bid_failed_total{reason}`, `bid_latency`, `active_auctions`, `websocket_connections`,
  `payment_success_total`. `docker compose --profile monitoring up` chạy Prometheus + Grafana với dashboard dựng
  sẵn; trong stack Docker, health và metric nằm ở cổng 8090 không công bố ra ngoài.
- **Analytics Consumer** (spec §19), theo quyết định đã chốt: consumer group `nexbid-analytics` cộng dồn theo giờ
  vào `analytics_hourly` (bid, lô đóng, lô bán được, thanh toán, doanh thu); trang Overview của admin hiện 24 giờ
  qua qua `GET /api/admin/analytics`. Test: `AnalyticsApiTest`.
- **Kafka Dead Letter Topic:** sự kiện không bao giờ xử lý được (không đọc được, hoặc bị database từ chối hẳn) được
  ghi log và gửi sang `nexbid.auctions.DLT` / `nexbid.payments.DLT`, giữ nguyên key, nội dung và header. Nếu gửi
  DLT cũng lỗi thì retry chứ không bỏ. Ghi chú vận hành cũ vẫn đúng: app bị kill thì consumer group chờ khoảng
  45 giây mới giao lại partition.
- **Quy tắc nhỏ**, theo quyết định đã chốt: không admin nào khoá được tài khoản ADMIN (kể cả chính mình) —
  `ADMIN_NOT_BLOCKABLE`; ảnh sản phẩm bị khoá cùng thông tin sản phẩm khi sản phẩm `IN_AUCTION` hoặc `SOLD`.
