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

## Đã làm (27/09/2026) — đợt 9: hiệu năng và CI

- **Hiệu năng trang (Lighthouse, mobile, trung vị 3 lần đo, so với bản trước trên cùng dữ liệu):** điểm tăng 5–6 ở
  mọi trang, LCP nhanh hơn 0.8–1.3 s, trang nhẹ hơn khoảng 30%, accessibility 100 ở cả bốn trang. Nguyên nhân chính:
  logo bị tải ở 1920/3840 px cho một hình 30 px (và cả bản sáng/tối đang ẩn); ảnh chưa dùng AVIF; ảnh lớn nhất của
  trang chưa được ưu tiên tải; font preload cả bộ tiếng Việt trên trang tiếng Anh. Cũng sửa ba lỗi accessibility mà
  axe trong test không bắt (tên đọc của nút ngôn ngữ, giao diện, ảnh trong gallery phải chứa chữ hiển thị; thứ tự
  heading ở Discover). Bảng số liệu nằm trong README → Frontend performance.
- **CI chưa từng xanh**, hai nguyên nhân, đã sửa:
  - Frontend: `tsc` cần các kiểu `PageProps`/`LayoutProps` do Next.js sinh ra; máy dev có sẵn nhờ `next dev`,
    còn CI checkout sạch thì chưa có. Thêm bước `next typegen` trước `tsc` (đã tái hiện đúng 15 lỗi rồi hết lỗi).
  - Backend: `AuctionAutoEndTest.theClosingBellWaitsForABidThatIsStillCommitting` đếm số lô được đóng, nhưng các
    test class khác để lại lô đã tới hạn trong database dùng chung; Linux chạy các class theo thứ tự khác Windows nên
    chỉ hỏng trên CI. Test giờ dọn các lô tới hạn trước khi đếm, như hai test đếm khác trong class (đã tái hiện
    đúng lỗi "expected 1 but was 2").
  - Vì job frontend hỏng nên job E2E và build image trên CI trước giờ luôn bị bỏ qua; lần push tới là lần đầu
    chúng chạy trên CI.

## Đã làm (27/09/2026) — đợt 10: CI xanh, độ phủ test, tài liệu thiết kế

- **CI xanh lần đầu** sau khi sửa (đợt 9): cả năm job qua, kể cả job E2E lần đầu chạy trên CI (31/31, không test
  nào phải chạy lại).
- **Độ phủ test:** backend dùng JaCoCo (94.7% dòng, 80.2% nhánh trên 215 class), frontend dùng Vitest cho tầng logic
  (97.5% dòng, 87.7% nhánh); ngưỡng thấp hơn một chút (backend 90%/75%, frontend 90%/80%) nên tụt thật sự thì CI
  hỏng; mỗi lần chạy CI ghi số liệu lên trang của lần chạy và giữ báo cáo HTML. Unit test frontend từ 75 lên 104:
  thêm test cho mapper (thông báo dẫn tới đâu, số tiền dạng chuỗi), store, bộ hẹn giờ gia hạn phiên, và các dạng lỗi
  của HTTP client.
- **Tài liệu quyết định thiết kế** (`docs/decisions.md`, tiếng Anh): mười một quyết định chính — vấn đề, lựa chọn,
  cái giá phải trả, và test chứng minh — được README trỏ tới.


## Đã làm (28/09/2026) — đợt 11: trở thành người bán, cơ hội thứ hai, tracing

- **Trở thành người bán** (spec §7.1, "User có thể đăng ký Seller sau này"): người mua gửi yêu cầu kèm vài dòng về
  thứ định bán; admin duyệt hoặc từ chối kèm lý do ở trang "Yêu cầu bán hàng"; bị từ chối thì gửi lại được. Mỗi người
  chỉ một yêu cầu đang chờ (khoá dòng tài khoản + index duy nhất có điều kiện, có test bốn tab gửi cùng lúc). Duyệt thì
  cấp vai trò SELLER trong cùng transaction với thông báo và audit. Vì vai trò nằm trong access token, trang đang mở tự
  gia hạn token ngay khi nhận thông báo được duyệt — dùng được công cụ người bán mà không phải đăng nhập lại. Nút "Đăng
  bán" ở header đưa người chưa là người bán tới trang gửi yêu cầu; khu vực người bán giải thích thay vì báo lỗi 403.
- **Cơ hội thứ hai** (spec §17, "offer cho người trả giá cao thứ hai"), theo lựa chọn: người bán quyết định, giá là
  giá cao nhất của chính người thứ hai, 24 giờ để nhận, mỗi lô một lần. Nhận thì lô đóng lại đứng tên người thứ hai
  và đi đúng đường thanh toán 48 giờ / đơn hàng như mọi giao dịch; từ chối hoặc hết hạn thì sản phẩm trở về người bán.
  Sản phẩm được giữ trong lúc chờ trả lời. Nút ở trang đơn hàng của người bán, mục "Đề nghị cơ hội thứ hai" ở My Wins;
  thông báo cho cả hai bên qua Kafka, audit cho từng bước. Một lô giờ có thể có hơn một khoản thanh toán / đơn hàng,
  nhưng không bao giờ hai cái cùng còn hiệu lực (index duy nhất có điều kiện).
- **Tracing OpenTelemetry** (spec §34, bản nâng cao): một lượt trả giá là một trace — request, từng câu SQL, outbox
  relay, lần gửi Kafka, và hai consumer cùng SQL của chúng — dù nửa sau chạy trên luồng khác. Outbox lưu
  `traceparent` của request để relay nối tiếp trace. Tempo trong profile monitoring, Grafana đọc được; bật bằng
  `NEXBID_TRACING_EXPORT=true`. Health check, lượt thu metric, lượt quét của relay và nhịp scheduler không tạo trace.
  Dòng log luôn mang trace id.
- **Kiểm chứng:** backend 384 test (độ phủ 95.1% dòng, 80.7% nhánh), frontend 110 unit test, E2E 33/33 trên stack
  Docker riêng có Tempo. Trace thật từ stack: 0 trace rác trong 3272 trace của một lần chạy E2E.

## Đã làm (28/09/2026) — đợt 12: đánh giá người bán, giá sàn ẩn, mua ngay (ngoài đặc tả, theo lựa chọn của chủ dự án)

- **Đánh giá người bán:** người mua chấm 1–5 sao kèm nhận xét ngắn tuỳ chọn, mỗi đơn một lần, chỉ sau khi đã xác
  nhận nhận hàng, không sửa được. Trang lô hiện điểm trung bình, số đánh giá, số lượt bán và ba đánh giá mới nhất
  (tên người viết được che như trong lịch sử trả giá). Người bán được báo mỗi đánh giá mới.
- **Giá sàn ẩn:** người trả giá chỉ thấy "chưa đạt / đã đạt giá sàn", cập nhật trực tiếp theo từng lượt; con số chỉ
  người bán và admin thấy. Đóng dưới giá sàn thì chưa có người thắng: người bán có 24 giờ để vẫn bán cho người trả
  cao nhất với giá của họ; từ chối hoặc im lặng thì sản phẩm trở về người bán. Auto bid có trần đủ giá sàn tự nâng
  lên giá sàn, để người sẵn lòng trả giá sàn không mất lô ở giá khởi điểm.
- **Mua ngay:** mua đứt lô ở giá cố định, chỉ tới lượt trả giá đầu tiên. Lượt mua được ghi như một lượt trả giá
  trong cùng khoá, rồi lô đóng ngay và đi đúng đường thanh toán 48 giờ.
- **Kiểm chứng:** backend 394 test (độ phủ 95.4% dòng, 81.8% nhánh), frontend 117 unit test, E2E 36/36 trên stack Docker
  riêng.

## Đã làm (12/11/2026) — đợt 13: thanh toán VNPay, email Brevo, giao hàng, trang quản trị

- **Thanh toán VNPay** thay cho công tắc demo. Backend ký link checkout (HMAC-SHA512) và kiểm phản hồi của VNPay ở cả
  IPN (server tới server) lẫn return URL (trình duyệt người mua). Mỗi lần thử là một dòng `payment_transactions`
  được khoá khi chốt, nên một khoản chỉ bị trừ một lần dù VNPay gửi lại IPN. Sai chữ ký hoặc sai số tiền thì bị từ
  chối; profile `prod` không chịu khởi động với khoá giữ chỗ.
- **Email giao dịch qua Brevo**, song ngữ Việt – Anh trong cùng một thư: chào mừng, thắng phiên, nhắc/biên nhận/hết
  hạn thanh toán, giao dịch bị huỷ, cơ hội thứ hai, duyệt/từ chối người bán, đã gửi hàng, bị vượt giá. Email được ghi
  vào `email_outbox` trong cùng transaction với sự kiện, job gửi có thử lại. Email bị vượt giá: tối đa một email mỗi
  lô mỗi 30 phút, nhưng gửi mọi lần trong 15 phút cuối.
- **Tắt email bị vượt giá**: công tắc trong hồ sơ, link một chạm có chữ ký trong email, và header `List-Unsubscribe`
  cho nút huỷ đăng ký của ứng dụng mail (RFC 8058). Email về tiền và hàng không tắt được.
- **Địa chỉ nhận hàng và mã vận đơn**: mỗi tài khoản một địa chỉ, sửa trong hồ sơ; chưa có địa chỉ thì không checkout
  được (`ADDRESS_REQUIRED`). Đơn đã trả giữ bản sao địa chỉ lúc thanh toán. Người bán nhập đơn vị vận chuyển và mã vận
  đơn khi gửi hàng; người mua thấy trong đơn và trong email.
- **Trang quản trị**: trang đăng nhập riêng `/admin/login` (chỉ tài khoản admin vào được), chặn `/admin` và `/seller`
  khi chưa đăng nhập hoặc không đủ quyền, trang quản lý danh mục. Các trang tài khoản người mua cũng chuyển người chưa
  đăng nhập sang trang đăng nhập rồi đưa quay lại.
- **Giao diện**: định dạng tiền theo ngôn ngữ (`18.500.000 ₫` / `₫18,500,000`), giá đếm dần khi có lượt trả giá mới,
  sửa menu đổi ngôn ngữ không mở được khi đang cuộn, sửa khung lượt trả giá đè lên khung đặt giá.
- **Staging**: `deploy/staging/` chạy cả stack sau Caddy với HTTPS tự động trên domain riêng.
- **Dữ liệu demo**: `docs/demo/enrich.mjs` thêm người bán, người mua và khoảng ba mươi lô ở mọi danh mục.

## Đã làm (13/11/2026) — đợt 14: sau thanh toán

- **Khiếu nại đơn hàng:** người mua báo vấn đề (chưa nhận, sai mô tả, hư hỏng, khác) với đơn đã trả, đã gửi hay chưa.
  Đơn chờ ở `DISPUTED` và không tự hoàn tất. Admin hoàn tiền (đơn huỷ) hoặc giữ giao dịch (đơn đã gửi thì hoàn tất,
  chưa gửi thì quay lại chờ người bán), kèm ghi chú cho cả hai bên.
- **Tự hoàn tất:** đơn đã gửi mà người mua không xác nhận và không khiếu nại sẽ hoàn tất sau 7 ngày. Người mua được
  nhắc một lần (trong app và email) 2 ngày trước đó (`NEXBID_ORDER_AUTO_COMPLETE_REMINDER`).
- **Chi trả cho người bán:** mỗi đơn hoàn tất thành khoản sàn nợ người bán (giá bán trừ phí 5%). Người bán nhập tài
  khoản ngân hàng; admin chuyển khoản rồi ghi nhận kèm mã giao dịch, chốt toàn bộ số dư một lần.
- **Hoàn tiền bằng chuyển khoản thủ công** (bỏ API hoàn tiền của VNPay, vì sandbox luôn trả lỗi 99): người mua lưu
  tài khoản nhận hoàn tiền trong hồ sơ hoặc ngay trong hộp khiếu nại; admin bấm hoàn tiền, thấy tài khoản để chép,
  tự chuyển khoản rồi nhập mã giao dịch. Hệ thống huỷ đơn, ghi `payment_refunds` kèm mã và bản sao tài khoản, báo
  người mua (email + thông báo `ORDER_REFUNDED`). Tài khoản ngân hàng dùng chung cho chi trả và hoàn tiền
  (`bank_accounts`, `/api/users/me/bank-account`), migration V29.
- **Thông báo:** email và thông báo trong app cho khiếu nại mở, kết quả khiếu nại (cả hai bên), tiền đã chuyển, và
  đơn sắp tự hoàn tất.
- **Nhật ký hệ thống:** mở khiếu nại, hoàn tiền / bác khiếu nại (kèm ghi chú), ghi nhận chi trả (số tiền, mã giao
  dịch), đổi tài khoản nhận tiền (chỉ lưu tên ngân hàng và 4 số cuối). Đơn hoàn tất sau khi bác khiếu nại giờ ghi đúng
  `DISPUTED → COMPLETED`.
- **E2E:** khiếu nại → admin giữ giao dịch; khiếu nại kèm tài khoản → admin hoàn tiền bằng chuyển khoản; người bán
  thêm tài khoản → admin ghi nhận chuyển khoản.
- **Sửa:** hộp thoại cao hơn màn hình không cuộn được nên nút cuối bị che (giờ giới hạn chiều cao và cuộn).
- **Sửa:** trạng thái đơn `DISPUTED` thiếu nhãn tiếng Việt / tiếng Anh, làm trang đơn hàng báo `MISSING_MESSAGE`.
- **Kiểm chứng:** backend 445 test (độ phủ 95.3% dòng, 81.7% nhánh), frontend 143 unit test, E2E 45/45 trên stack
  Docker riêng.

## Đã làm (14/11/2026) — đợt 15: hỏi đáp trên trang lô

- **Hỏi đáp công khai:** trên trang lô, người mua đã đăng nhập hỏi khi lô sắp mở hoặc đang mở (5–500 ký tự); người bán
  của lô trả lời một lần, ngay dưới câu hỏi. Ai cũng đọc được, tên người hỏi được che như lịch sử trả giá. Người bán
  không tự hỏi lô của mình; mỗi người tối đa 3 câu đang chờ trên một lô (`QUESTION_LIMIT`); lô đã đóng thì không nhận
  câu hỏi. Bảng `lot_questions`, migration V30, module `question`.
- **Thông báo:** người bán nhận email `LOT_QUESTION` và thông báo `NEW_QUESTION`; người hỏi nhận email
  `QUESTION_ANSWERED` và thông báo cùng tên. Bấm thông báo mở thẳng phần hỏi đáp của lô.
- **E2E:** khách thấy link đăng nhập; người mua hỏi; người bán trả lời; người mua thấy câu trả lời và thông báo.
- **Kiểm chứng:** backend 449 test (độ phủ 95.4% dòng, 81.9% nhánh), frontend 145 unit test, E2E 45/45 trên stack Docker
  riêng.
