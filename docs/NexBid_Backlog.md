# NEXBID — BACKLOG SAU 40 CHỨC NĂNG

Backend đã xong đủ 40 chức năng của `NexBid_Implementation_Guide_Step_By_Step.md`. File này ghi lại
những phần **spec có nhắc tới nhưng guide không có bước nào làm**, cùng các cải thiện phát hiện được khi
review và load test. Mỗi mục có: spec nói gì, hiện trạng, hướng làm, cách kiểm thử.

Các mục đã làm nằm ở cuối file, kèm các quyết định nghiệp vụ đã chốt.

---

Hiện không còn mục nào đang chờ.

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

## Đã làm (27/09/2026) — đợt 3

- **Dọn môi trường dev:** DB dev đã được xoá sạch và đổ lại bằng `docs/demo/seed.mjs` (README → Demo data có
  lệnh để làm lại khi cần).
- **Múi giờ hiển thị**, theo quyết định đã chốt: mọi mốc giờ hiện theo múi giờ trình duyệt của người xem. Trình
  duyệt báo múi giờ qua cookie `NEXBID_TZ`, server đọc nó cho next-intl (chưa có cookie thì dùng UTC) và trang
  render lại một lần ở lần vào đầu tiên. Danh sách bid realtime giờ cũng định dạng qua next-intl, nên cùng một
  bid hiện cùng một giờ ở mọi màn hình; form tạo lô nhập và xem trước theo cùng múi giờ.
- **Test end-to-end** (Playwright, `frontend/e2e`): xem lô khi chưa đăng nhập, đăng nhập/sai mật khẩu/đăng ký,
  hai người trả giá thấy bid của nhau không cần tải lại, chặn giá dưới mức tối thiểu, admin duyệt lô, và giờ theo
  múi giờ người xem. Mỗi lần chạy tự tạo tài khoản và lô riêng. Chạy trong CI trên stack `docker compose`; đẩy
  image chỉ xảy ra khi test này qua.
- **Ảnh demo đúng sản phẩm:** thay các ảnh Unsplash sai sản phẩm trong seed (Omega là ảnh IWC, Leica là Fujifilm,
  Sony và Canon AE-1 là máy Canon khác, Birkin là túi Ferragamo/Gucci, Vision Pro là kính Oculus, Sacai là giày
  khác, Stratocaster là đàn Gibson...). Unsplash không có ảnh Birkin miễn phí, nên lô Birkin dùng ảnh túi da không
  lộ thương hiệu khác.
- **Gợi ý tài khoản demo ở trang đăng nhập:** câu cũ từ thời dữ liệu giả ("pin@nexbid.com với mật khẩu bất
  kỳ") không còn đúng với backend thật; giờ chỉ đúng tài khoản của seed demo (`pin@nexbid.test` / `nexbid-demo`).

## Đã làm (27/09/2026) — đợt 4: chất lượng giao diện

- **Accessibility (WCAG 2.1 AA, kiểm bằng axe):** sửa độ tương phản của chữ phụ ở cả hai giao diện (`--dim`,
  `--muted-foreground`: tối thiểu 4.5:1 trên mọi nền); tab lọc giờ có tab panel thật (hết `aria-controls` trỏ vào
  hư không); giá và đồng hồ đọc qua chữ ẩn thay vì `aria-label` trên thẻ span; ô chọn ảnh có nhãn; bảng cuộn ngang
  trên điện thoại dùng bàn phím được; khung bên của trang đăng nhập/đăng ký dùng danh sách đúng chuẩn; số lô chìm
  làm nền được đánh dấu là trang trí.
- **Mobile (375 px):** các trang tài khoản không còn rộng 663 px (cột grid giờ bằng màn hình, thanh tab tự cuộn
  ngang); thanh tab nào dài hơn màn hình cũng tự cuộn; ô tìm kiếm ở trang Discover chiếm cả dòng trên điện thoại.
- **Chữ trên ảnh bìa trang chủ:** dòng "Opening lot · …" có đổ bóng như tiêu đề, nên vẫn đọc được trên ảnh sáng.
- **Test:** `accessibility.spec.ts` (axe trên mọi trang, mọi vai trò, cả hai giao diện), `mobile.spec.ts` (không
  trang nào rộng hơn màn hình, axe ở khổ điện thoại, trả giá từ điện thoại), và mọi test giờ trượt nếu trang có lỗi
  console, lệch hydration hay exception. Mỗi kiểm tra đã được thử bằng cách cố tình làm hỏng code nó canh.

## Đã làm (27/09/2026) — đợt 5: refresh token (spec §7.2)

- Theo quyết định đã chốt: access token 15 phút; refresh token 7 ngày, tính lại từ lần dùng cuối; "Remember me"
  có tác dụng thật (bỏ tick thì cookie chỉ sống tới khi đóng trình duyệt); khoá tài khoản thu hồi mọi phiên của
  người đó.
- Refresh token nằm trong cookie `HttpOnly; Secure; SameSite=Strict` chỉ gửi tới `/api/auth`; DB chỉ lưu SHA-256
  (`refresh_tokens`, V18). Mỗi token dùng một lần; token đã bị thay mà quay lại thì cả họ token bị thu hồi.
  Endpoint mới: `POST /api/auth/refresh`, `POST /api/auth/logout`. Token hết hạn được dọn định kỳ.
- Frontend gia hạn trước khi hết hạn một phút, thử lại một lần sau 401, dùng Web Lock để các tab không gia hạn
  cùng lúc, và chỉ kết nối lại socket khi người đăng nhập thay đổi. Đăng xuất gọi server.
- Sửa kèm: trên desktop trước đây không có nút đăng xuất (nút chỉ nằm trong menu điện thoại); giờ có ở cột tài
  khoản.
- Test: `RefreshTokenApiTest` (cookie, xoay vòng, dùng lại, đăng xuất theo thiết bị, khoá tài khoản, hết hạn,
  chỉ lưu hash) và `session.spec.ts` (phiên sống qua lúc access token hết hạn, "Remember me", đăng xuất).

## Đã làm (27/09/2026) — đợt 6: unit test cho frontend

- Vitest (`npm test`), test nằm cạnh code (`src/**/*.test.ts`), chạy trong CI trước bước build: luật trả giá phía
  client (mức tối thiểu, thứ tự kiểm tra, auto bid, anti-sniping), định dạng tiền và đồng hồ, bộ lọc trên URL, che
  tên người trả giá, và phần phiên đăng nhập (gia hạn trước khi hết hạn, thử lại một lần sau 401, không lặp vô hạn,
  một lần gia hạn cho nhiều request, bỏ qua khi tab khác đã gia hạn).
- Lỗi tìm ra nhờ test, đã sửa:
  - Sau một 401, client thấy token "còn hạn" nên bỏ qua bước gia hạn và thử lại bằng đúng token vừa bị từ chối,
    rồi đăng xuất người dùng. Giờ token bị từ chối luôn được thay.
  - Tab nào bắt đầu bằng việc đăng nhập thì không theo được tab khác (listener `storage` chỉ gắn ở lần đọc đầu
    tiên). Giờ gắn ở bất kỳ lần truy cập đầu tiên nào.
  - Ô auto bid đọc "95.000.000" (cách nhóm số tiếng Việt) thành rỗng. VND không có phần lẻ nên mọi dấu phân cách
    giờ đều là dấu nhóm.
  - `getQuickBidAmounts` tính sai (1x/2x/5x thành min, min+2x, min+10x). Hàm này chưa được dùng ở đâu, nhưng giờ
    đã đúng như chú thích.
- `@types/node` nâng từ 20 lên 22 cho khớp Node 22 mà CI và image Docker đang chạy (Vitest 5 cần).

## Đã làm (27/09/2026) — đợt 7: bảo mật

- **Giới hạn đăng nhập sai**, theo quyết định đã chốt: 5 lần sai trong 15 phút cho mỗi cặp email + địa chỉ, và 20
  lần cho mỗi địa chỉ; vượt thì trả `429 LOGIN_RATE_LIMITED` kèm `Retry-After`, kể cả khi mật khẩu đúng. Mật khẩu
  đúng thì xoá bộ đếm của cặp đó. Đếm trong Redis (khung trượt, Redis sập thì cho qua). Địa chỉ client lấy từ
  `X-Forwarded-For`, chỉ tin khi đến từ địa chỉ nội bộ (`server.forward-headers-strategy: native`). Đã kiểm tra
  trên stack Docker: Next.js chuyển tiếp header này nhưng không tự đặt nó, nên khi deploy phải có reverse proxy
  (vốn cần cho HTTPS) đặt header; không có thì mọi trình duyệt trông như cùng một địa chỉ của frontend.
- **Content Security Policy** cho mọi trang, nonce mới mỗi request (`src/proxy.ts`), kèm nosniff, X-Frame-Options,
  Referrer-Policy, Permissions-Policy, HSTS; bỏ header `X-Powered-By`.
- **Profile `prod`**, theo quyết định đã chốt: không khởi động nếu JWT secret là giá trị mặc định trong repo hoặc
  ngắn hơn 32 byte, hay cookie refresh không bắt buộc HTTPS; tắt Swagger UI và `/v3/api-docs`.
- Test: `LoginRateLimitTest`, `ProductionGuardTest`, `ProductionProfileTest`, `security.spec.ts` (CSP và nonce,
  chặn handler inline bị chèn vào trang, form đăng nhập báo giới hạn).

## Đã làm (27/09/2026) — đợt 8: tiếng Việt và dọn phần còn sót từ thời dữ liệu giả

- **Thông báo lỗi theo ngôn ngữ người đọc:** lỗi đăng nhập/đăng ký trước đây viết cứng bằng tiếng Anh trong code,
  và 10 màn hình lỗi hiện nguyên văn thông báo tiếng Anh của server. Giờ tất cả dịch theo mã lỗi
  (`useAuthErrorMessage`, `ApiErrorState`); thêm bản dịch cho `REFRESH_TOKEN_INVALID` và `LOGIN_RATE_LIMITED`.
  Nhãn cho trình đọc màn hình (Filters, Main, Menu, Breadcrumb, Close) cũng đã dịch.
- **Test giữ hai bộ thông điệp khớp nhau** (`src/i18n/messages.test.ts`): cùng khoá, cùng placeholder ở hai ngôn ngữ,
  có thông điệp cho mọi mã lỗi, và danh sách mã lỗi của frontend phải trùng `ErrorCode.java`. E2E
  `vietnamese.spec.ts` kiểm tra người đọc tiếng Việt thấy lỗi bằng tiếng Việt, không phải tiếng Anh của server.
- **Dọn code thừa (knip):** xoá 13 component UI không dùng, 18 hàm/hằng/kiểu chết (các hàm service không ai gọi,
  `isLive`/`isUpcoming`/..., `formatNumber`, `MAIN_NAV`, `PRICE_FILTER`, các kiểu envelope cũ), bỏ `export` ở những
  thứ chỉ dùng trong file; sửa các chú thích còn nói về "mock". `npm run knip` chạy trong CI.

