# HƯỚNG DẪN TRIỂN KHAI NEXBID THEO TỪNG CHỨC NĂNG
## Làm từng chức năng một, theo đúng thứ tự phụ thuộc

**Dự án:** NexBid — Hệ thống đấu giá trực tuyến thời gian thực  
**Mục tiêu:** Hướng dẫn triển khai project cá nhân theo từng chức năng, tránh làm lan man và tránh nhảy cóc.

---

# 1. Nguyên tắc thực hiện

Mỗi chức năng phải hoàn thành theo thứ tự:

```text
1. Xác định nghiệp vụ
2. Thiết kế database
3. Tạo Entity / Model
4. Tạo Repository
5. Tạo Service
6. Tạo Controller / API
7. Test API bằng Postman / Swagger
8. Làm giao diện frontend
9. Kết nối frontend với backend
10. Test lại toàn luồng
11. Commit Git
12. Chuyển sang chức năng tiếp theo
```

Không làm nhiều chức năng cùng lúc.

Ví dụ:

```text
Không làm:
Login + Product + Auction + Redis cùng lúc

Nên làm:
Register xong
↓
Login xong
↓
Profile xong
↓
Product xong
↓
Auction xong
```

Mỗi chức năng nên có một commit riêng.

Ví dụ:

```text
feat: implement user registration

feat: implement login with JWT

feat: implement seller product creation
```

---

# 2. Thứ tự tổng thể

Thực hiện theo thứ tự:

```text
01. Khởi tạo project
02. Chuẩn hóa response và exception
03. User Entity + Role
04. Đăng ký
05. Đăng nhập JWT
06. Authorization theo Role
07. Profile
08. Category
09. Seller tạo Product
10. Upload Product Image
11. Seller quản lý Product
12. Seller tạo Auction
13. Seller gửi Auction duyệt
14. Admin xem Auction chờ duyệt
15. Admin Approve / Reject
16. Public Auction List
17. Auction Detail
18. Basic Place Bid
19. Concurrent Bid Locking
20. Bid History
21. WebSocket Realtime Bid
22. Countdown đồng bộ server
23. Auction Auto Start
24. Auction Auto End
25. Winner Selection
26. Watchlist
27. Notification cơ bản
28. Anti-Sniping
29. Auto Bid
30. Mock Payment
31. Order
32. Redis Cache
33. Rate Limiting
34. Kafka Event
35. Audit Log
36. Integration Test
37. Concurrency Test
38. Docker Compose
39. Load Test
40. README + Demo
```

---

# PHASE 0 — PROJECT FOUNDATION

# 3. Chức năng 01 — Khởi tạo Project

## Mục tiêu

Tạo skeleton cho cả frontend và backend.

## Backend

Tạo Spring Boot project với:

```text
Spring Web
Spring Data JPA
Spring Security
Validation
PostgreSQL Driver
Lombok
WebSocket
Actuator
```

Các dependency Redis/Kafka có thể thêm sau.

Bản triển khai thực tế: Spring Boot 4.1.1 trên Java 21, Maven. Lưu ý Boot 4 khác
Boot 3 ở vài chỗ: starter web là `spring-boot-starter-webmvc`, các starter test
tách theo từng module, và Jackson 3 (`tools.jackson`) thay cho Jackson 2.

Package:

```text
com.nexbid
```

Structure ban đầu:

```text
backend/
frontend/
docs/
docker/
```

## Frontend

Tạo:

```text
Next.js
TypeScript
Tailwind CSS
```

Có thể dùng:

```text
shadcn/ui
```

## Database

Tạo database:

```text
nexbid
```

Chạy bằng `docker/compose.yaml`. Postgres publish ở cổng **55432** chứ không phải
5432 — trên máy dev cả 5432 và 5433 đều đã bị chiếm, và khi cổng bị tranh chấp
Docker có thể chỉ bind được phía IPv6, khiến `localhost` trỏ nhầm sang server
khác. Lỗi lúc đó trông y hệt sai mật khẩu, rất mất thời gian truy.

## Tiêu chí hoàn thành

Backend:

```http
GET /api/health
```

trả về:

```json
{
  "status": "UP"
}
```

Frontend chạy được trang home.

## Commit

```text
chore: initialize NexBid frontend and backend
```

---

# 4. Chức năng 02 — Chuẩn hóa API Response và Exception

## Mục tiêu

Trước khi code nghiệp vụ, toàn backend phải có format response thống nhất.

## Tạo ApiResponse

Ví dụ:

```java
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
}
```

## Tạo ErrorResponse

```java
public class ErrorResponse {
    private boolean success;
    private String code;
    private String message;
    private LocalDateTime timestamp;
}
```

## Tạo GlobalExceptionHandler

Xử lý:

```text
ValidationException
ResourceNotFoundException
AccessDeniedException
BusinessException
MethodArgumentNotValidException
```

## Tiêu chí hoàn thành

API lỗi phải trả về cùng một format.

Ví dụ:

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "Email is invalid",
  "timestamp": "..."
}
```

## Commit

```text
feat: add global API response and exception handling
```

---

# PHASE 1 — AUTHENTICATION

# 5. Chức năng 03 — User Entity + Role

## Mục tiêu

Tạo nền tảng user trước khi làm register/login.

## Database

Tạo:

```text
users
roles
user_roles
```

## User Entity

Fields:

```text
id
fullName
email
password
status
createdAt
updatedAt
```

## Role

```text
BUYER
SELLER
ADMIN
```

Có thể dùng Enum nếu muốn đơn giản:

```java
public enum RoleName {
    BUYER,
    SELLER,
    ADMIN
}
```

## User Status

```text
ACTIVE
BLOCKED
```

## Migration

Schema do **Flyway** sở hữu, không để Hibernate tự sinh: `ddl-auto` đặt
`validate` ngay từ bước 01. Lý do là SQL nằm trong repo thì review được và chạy
lại ở máy khác cho ra kết quả y hệt; `ddl-auto: update` đổi schema ngầm và không
bao giờ xoá được cột thừa. Thứ tự file migration theo đúng §44.

File đầu tiên: `V1__create_users_and_roles.sql` — tạo `roles`, `users`,
`user_roles`, và chèn sẵn ba vai trò.

Email là định danh đăng nhập nên ràng buộc duy nhất phải bỏ qua hoa thường
(`CREATE UNIQUE INDEX ... ON users (LOWER(email))`), nếu không `A@x.com` và
`a@x.com` sẽ cùng đăng ký được.

Khoá chính của `users` dùng **UUID** chứ không phải số tăng dần: hợp đồng với
frontend khai mọi id là chuỗi, và id không để lộ số lượng người dùng.

## Test

Test chạy trên Postgres thật qua Testcontainers, không dùng H2 — schema có
`LOWER()` index, `UUID` và `TIMESTAMPTZ` nên H2 sẽ kiểm chứng sai sự thật.
Nhờ vậy `./mvnw test` cũng không cần database dev phải đang bật.

## Tiêu chí hoàn thành

Application chạy, Flyway apply migration thành công, và Hibernate validate được
entity khớp với schema.

## Commit

```text
feat: create user and role domain models
```

---

# 6. Chức năng 04 — Đăng ký

## Mục tiêu

User tạo tài khoản.

## API

```http
POST /api/auth/register
```

## Request

```json
{
  "fullName": "Nguyen Van A",
  "email": "a@example.com",
  "password": "12345678"
}
```

## Backend cần làm

### DTO

```text
RegisterRequest
RegisterResponse
```

### Validation

```text
fullName: required
email: required + valid email
password: >= 8 characters
```

### Service

Flow:

```text
Receive Request
↓
Check Email Exists
↓
Hash Password
↓
Set Role BUYER
↓
Set Status ACTIVE
↓
Save User
↓
Return User
```

## Business Rules

- Email không được trùng.
- Password không lưu plain text.
- Role mặc định BUYER.

## Frontend

Tạo page:

```text
/register
```

Form:

```text
Full Name
Email
Password
Confirm Password
```

## Test Cases

```text
Register valid → SUCCESS
Duplicate email → FAIL
Invalid email → FAIL
Password too short → FAIL
```

## Tiêu chí hoàn thành

User đăng ký từ frontend và dữ liệu xuất hiện trong DB.

## Commit

```text
feat: implement user registration
```

---

# 7. Chức năng 05 — Đăng nhập JWT

## Mục tiêu

Cho phép user đăng nhập và nhận token.

## API

```http
POST /api/auth/login
```

## Request

```json
{
  "email": "a@example.com",
  "password": "12345678"
}
```

## Response

```json
{
  "success": true,
  "data": {
    "accessToken": "...",
    "user": {
      "id": 1,
      "fullName": "Nguyen Van A",
      "email": "a@example.com",
      "roles": ["BUYER"]
    }
  }
}
```

## Backend

Tạo:

```text
JwtService
JwtAuthenticationFilter
SecurityConfig
CustomUserDetailsService
```

## Flow

```text
Email + Password
↓
AuthenticationManager
↓
Validate Password
↓
Generate JWT
↓
Return Token
```

## Frontend

Page:

```text
/login
```

Khi login thành công:

- lưu access token;
- lưu user state;
- redirect `/`.

## Test Cases

```text
Correct account → SUCCESS
Wrong password → FAIL
Unknown email → FAIL
Blocked user → FAIL
```

## Lựa chọn khi triển khai

Thư viện JWT dùng **jjwt** (`io.jsonwebtoken`), tách ba artifact api/impl/jackson để
code chỉ biên dịch trên phần api.

Secret đặt ở `NEXBID_JWT_SECRET`, có giá trị mặc định cho dev. HMAC-SHA256 cần khoá
từ 32 byte trở lên; khoá ngắn hơn sẽ lỗi ngay lúc khởi động thay vì âm thầm làm token yếu.

Email sai và mật khẩu sai trả về **cùng một lỗi** `INVALID_CREDENTIALS`, cùng câu chữ.
Nếu phân biệt, endpoint này thành công cụ dò xem địa chỉ nào đã có tài khoản.

Tài khoản `BLOCKED` trả `ACCOUNT_BLOCKED` (403), khác với sai mật khẩu — đây là lần
đầu `UserStatus` có tác dụng thật kể từ chức năng 03.

## Tiêu chí hoàn thành

Gọi API protected với JWT thành công.

## Commit

```text
feat: implement JWT authentication
```

---

# 8. Chức năng 06 — Authorization theo Role

## Mục tiêu

Phân quyền BUYER / SELLER / ADMIN.

## Backend

Thiết lập:

```text
/api/admin/** → ADMIN
/api/seller/** → SELLER
```

Có thể dùng:

```java
@PreAuthorize("hasRole('ADMIN')")
```

## Test

BUYER gọi:

```http
GET /api/admin/test
```

phải nhận:

```text
403 Forbidden
```

ADMIN gọi phải thành công.

## Frontend

Ẩn menu theo role:

```text
BUYER → My Bids, Watchlist
SELLER → Seller Dashboard
ADMIN → Admin Dashboard
```

Lưu ý:

Frontend chỉ ẩn UI.

Backend mới là nơi thực sự bảo vệ permission.

## Tài khoản ADMIN đầu tiên

Mọi tài khoản đăng ký đều là BUYER, nên phải có đường cấp quyền nằm ngoài API công khai.
Nhét cứng một admin vào migration sẽ phát tán một mật khẩu ai cũng biết, nên thay vào đó
ứng dụng cấp vai trò lúc khởi động theo cấu hình:

```bash
NEXBID_ADMIN_EMAILS=you@example.com ./mvnw spring-boot:run
```

Email chưa đăng ký thì chỉ ghi cảnh báo, không làm chết ứng dụng.

Lưu ý ADMIN **không** bao hàm SELLER: spec §4 định nghĩa ba vai trò là ba công việc
khác nhau, không phải ba cấp bậc.

## Commit

```text
feat: add role based authorization
```

---

# 9. Chức năng 07 — Profile

## API

```http
GET /api/users/me
PUT /api/users/me
```

## User có thể sửa

```text
fullName
```

Không cho tự sửa:

```text
id
role
status
```

## Frontend

```text
/profile
```

## Nguyên tắc khi triển khai

Id của người gọi lấy từ **token**, không bao giờ từ body. Nếu nhận từ body thì ai cũng
đọc và sửa được hồ sơ người khác chỉ bằng cách đổi một con số.

Danh sách cấm sửa là danh sách trắng chứ không phải danh sách đen: `UpdateProfileRequest`
chỉ có đúng field `fullName`, nên `role`, `status`, `id` không phải bị chặn — chúng
không tồn tại để mà gửi lên.

Đây là endpoint đầu tiên cần biết cụ thể ai đang gọi, nên sinh ra kiểu `CurrentUser`.
Nó đặt ở module `common` chứ không phải `auth`: `auth` vốn đã phụ thuộc `user`, thêm
chiều ngược lại sẽ tạo vòng lặp phụ thuộc và Spring Modulith sẽ báo đỏ.

## Tiêu chí hoàn thành

User login và xem/chỉnh profile được.

## Commit

```text
feat: implement user profile
```

---

# PHASE 2 — PRODUCT

# 10. Chức năng 08 — Category

## Mục tiêu

Tạo category trước Product.

## Table

```text
categories
```

Fields:

```text
id
name
slug
status
```

Ví dụ:

```text
Electronics
Fashion
Collectibles
Watches
Art
```

## API

Public:

```http
GET /api/categories
```

Admin:

```http
POST /api/admin/categories
PUT /api/admin/categories/{id}
```

## Tiêu chí hoàn thành

Frontend lấy được danh sách category.

## Commit

```text
feat: implement product categories
```

---

# 11. Chức năng 09 — Seller tạo Product

## Điều kiện

User phải có role:

```text
SELLER
```

## API

```http
POST /api/seller/products
```

## Request

```json
{
  "name": "MacBook Pro M3",
  "description": "...",
  "categoryId": 1,
  "condition": "LIKE_NEW"
}
```

## Product Status

```text
DRAFT
ACTIVE
INACTIVE
```

## Backend

Flow:

```text
Authenticated Seller
↓
Validate Category
↓
Create Product
↓
sellerId = currentUser.id
↓
Save
```

## Frontend

Page:

```text
/seller/products/create
```

## Tiêu chí hoàn thành

Seller tạo product và product thuộc đúng seller.

## Commit

```text
feat: implement seller product creation
```

---

# 12. Chức năng 10 — Upload Product Image

## Mục tiêu

Cho Product có nhiều ảnh.

## Table

```text
product_images
```

Fields:

```text
id
product_id
image_url
sort_order
```

## Version đầu

Có thể:

```text
upload local
```

Version sau:

```text
Cloudinary / S3
```

## API

```http
POST /api/seller/products/{id}/images
DELETE /api/seller/products/{id}/images/{imageId}
```

## Rule

Seller chỉ sửa ảnh của product mình sở hữu.

## Frontend

- preview ảnh;
- chọn ảnh cover;
- xóa ảnh.

## Commit

```text
feat: implement product image upload
```

---

# 13. Chức năng 11 — Seller quản lý Product

## API

```http
GET /api/seller/products
GET /api/seller/products/{id}
PUT /api/seller/products/{id}
DELETE /api/seller/products/{id}
```

## Rules

- Seller chỉ thấy product của mình.
- Product đã có auction ACTIVE thì không được delete.

## Frontend

```text
/seller/products
```

Có:

```text
Create
Edit
Delete
View
```

## Commit

```text
feat: implement seller product management
```

---

# PHASE 3 — AUCTION

# 14. Chức năng 12 — Seller tạo Auction

## Mục tiêu

Biến Product thành một phiên đấu giá.

## Table

```text
auctions
```

## Fields

```text
id
product_id
seller_id
starting_price
current_price
minimum_increment
start_time
end_time
status
anti_sniping_enabled
anti_sniping_window_seconds
extension_seconds
winner_id
version
created_at
updated_at
```

## API

```http
POST /api/seller/auctions
```

## Request

```json
{
  "productId": 1,
  "startingPrice": 10000000,
  "minimumIncrement": 500000,
  "startTime": "...",
  "endTime": "...",
  "antiSnipingEnabled": false
}
```

## Validation

```text
Product phải thuộc Seller
Start Time < End Time
Starting Price > 0
Minimum Increment > 0
Product chưa có Auction active khác
```

## Status ban đầu

```text
DRAFT
```

## Frontend

```text
/seller/auctions/create
```

## Tiêu chí hoàn thành

Seller tạo Auction DRAFT.

## Commit

```text
feat: implement auction creation
```

---

# 15. Chức năng 13 — Seller gửi Auction duyệt

## API

```http
POST /api/seller/auctions/{id}/submit
```

## Flow

```text
DRAFT
↓
Validate Auction
↓
PENDING_APPROVAL
```

## Rule

Chỉ DRAFT mới submit được.

Sau khi submit:

- không cho seller sửa;
- phải chờ admin.

## Commit

```text
feat: implement auction submission for approval
```

---

# 16. Chức năng 14 — Admin xem Auction chờ duyệt

## API

```http
GET /api/admin/auctions/pending
GET /api/admin/auctions/{id}
```

## Frontend

```text
/admin/auctions
```

Hiển thị:

```text
Product
Seller
Starting Price
Start Time
End Time
Images
Description
```

## Commit

```text
feat: add pending auction review for admin
```

---

# 17. Chức năng 15 — Admin Approve / Reject

## API

```http
POST /api/admin/auctions/{id}/approve
POST /api/admin/auctions/{id}/reject
```

Reject request:

```json
{
  "reason": "Invalid product information"
}
```

## Approve Flow

```text
PENDING_APPROVAL
↓
Nếu startTime > now
→ SCHEDULED

Nếu startTime <= now và endTime > now
→ ACTIVE
```

## Reject

```text
PENDING_APPROVAL
↓
REJECTED
```

## Tiêu chí hoàn thành

Auction chỉ public khi đã được admin approve.

## Commit

```text
feat: implement auction approval workflow
```

---

# PHASE 4 — PUBLIC AUCTION

# 18. Chức năng 16 — Public Auction List

## API

```http
GET /api/auctions
```

## Filter

```text
status
category
price
endingSoon
```

## Sort

```text
NEWEST
ENDING_SOON
PRICE_ASC
PRICE_DESC
MOST_BIDS
```

## Pagination

Ví dụ:

```http
GET /api/auctions?page=0&size=12
```

## Frontend

Page:

```text
/auctions
```

Card:

```text
Image
Name
Current Price
Bid Count
Countdown
Status
```

## Commit

```text
feat: implement public auction listing
```

---

# 19. Chức năng 17 — Auction Detail

## API

```http
GET /api/auctions/{id}
```

## Response nên có

```text
Product
Images
Seller
Starting Price
Current Price
Minimum Increment
Minimum Next Bid
Bid Count
Start Time
End Time
Status
Server Time
```

## Frontend

```text
/auctions/[id]
```

Chưa cần realtime ở bước này.

## Commit

```text
feat: implement auction detail page
```

---

# PHASE 5 — CORE BIDDING

# 20. Chức năng 18 — Basic Place Bid

## Đây là chức năng quan trọng nhất

Chỉ làm bản tuần tự trước.

## Table

```text
bids
```

Fields:

```text
id
auction_id
bidder_id
amount
created_at
```

## API

```http
POST /api/auctions/{id}/bids
```

## Request

```json
{
  "amount": 10500000
}
```

## Service Flow

```text
Find Auction
↓
Check ACTIVE
↓
Check now < endTime
↓
Check bidder != seller
↓
Calculate minimum bid
↓
Validate amount
↓
Create Bid
↓
Update currentPrice
↓
Save
```

## Minimum Bid

Nếu chưa có bid:

```text
minimumBid = startingPrice
```

Nếu có bid:

```text
minimumBid = currentPrice + minimumIncrement
```

## Test

```text
Valid Bid → SUCCESS
Low Bid → FAIL
Seller bids → FAIL
Auction ended → FAIL
Auction scheduled → FAIL
```

## Chưa làm

- Locking.
- WebSocket.
- Redis.
- Kafka.

## Commit

```text
feat: implement basic auction bidding
```

---

# 21. Chức năng 19 — Concurrent Bid Locking

## Mục tiêu

Chống race condition.

## Repository

Tạo method load Auction bằng:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

## Transaction

Place Bid Service:

```java
@Transactional
```

## Flow

```text
Request A
↓
Lock Auction
↓
Validate
↓
Save Bid
↓
Update Price
↓
Commit

Request B chờ
↓
Load lại Current Price
↓
Validate lại
↓
Reject nếu thấp
```

## Test bắt buộc

Current:

```text
20m
```

100 request cùng bid:

```text
21m
```

Expected:

```text
1 SUCCESS
99 FAIL / CONFLICT
```

## Tiêu chí hoàn thành

Không còn race condition ở bid.

## Commit

```text
feat: add pessimistic locking for concurrent bidding
```

---

# 22. Chức năng 20 — Bid History

## API

```http
GET /api/auctions/{id}/bids
```

## Sort

```text
createdAt DESC
```

## Không expose

Thông tin nhạy cảm của bidder.

Hiển thị dạng:

```text
pin***
alex***
```

## Frontend

Auction Detail có section:

```text
Bid History
```

## Commit

```text
feat: add auction bid history
```

---

# PHASE 6 — REALTIME

# 23. Chức năng 21 — WebSocket Realtime Bid

## Mục tiêu

Bid thành công thì mọi browser đang xem auction cập nhật ngay.

## Backend

Config WebSocket.

Topic:

```text
/topic/auctions/{auctionId}
```

Sau khi transaction bid thành công:

```text
publish BID_PLACED
```

Payload:

```json
{
  "type": "BID_PLACED",
  "auctionId": 1,
  "currentPrice": 10500000,
  "bidCount": 12,
  "bidder": "pin***",
  "createdAt": "..."
}
```

## Frontend

Subscribe khi mở Auction Detail.

Khi nhận event:

```text
update currentPrice
update minimumNextBid
update bidCount
prepend bidHistory
```

## Test

Mở hai browser.

Browser A bid.

Browser B phải thấy giá đổi ngay.

## Commit

```text
feat: add realtime bidding with WebSocket
```

---

# 24. Chức năng 22 — Countdown đồng bộ Server

## Mục tiêu

Không phụ thuộc clock của máy user.

## API Detail trả thêm

```text
serverTime
endTime
```

Frontend tính:

```text
remaining = endTime - serverTime
```

Sau đó countdown client-side.

Định kỳ có thể sync lại server.

## Rule

Frontend countdown chỉ để hiển thị.

Backend vẫn kiểm tra:

```text
now < endTime
```

mỗi khi bid.

## Commit

```text
feat: synchronize auction countdown with server time
```

---

# PHASE 7 — AUCTION LIFECYCLE

# 25. Chức năng 23 — Auction Auto Start

## Mục tiêu

Auction tự chuyển:

```text
SCHEDULED → ACTIVE
```

khi đến `startTime`.

## Scheduler

Ví dụ:

```text
5 giây / lần
```

Query:

```text
status = SCHEDULED
AND startTime <= now
```

Update:

```text
ACTIVE
```

## Frontend

WebSocket có thể broadcast:

```text
AUCTION_STARTED
```

## Commit

```text
feat: add automatic auction start scheduler
```

---

# 26. Chức năng 24 — Auction Auto End

## Mục tiêu

Auction tự hết hạn.

Scheduler query:

```text
status = ACTIVE
AND endTime <= now
```

Lock Auction.

Update:

```text
ENDED
```

Chưa cần payment ở bước này.

## Commit

```text
feat: add automatic auction closing
```

---

# 27. Chức năng 25 — Winner Selection

## Logic

Khi Auction End:

```text
Find highest valid bid
```

Nếu không có bid:

```text
winnerId = null
```

Nếu có:

```text
winnerId = highestBid.bidderId
finalPrice = highestBid.amount
```

## Rule

Winner được xác định duy nhất ở backend.

## API User

```http
GET /api/users/me/wins
```

## Frontend

Page:

```text
/my-wins
```

## Commit

```text
feat: implement auction winner selection
```

---

# PHASE 8 — USER ENGAGEMENT

# 28. Chức năng 26 — Watchlist

## Table

```text
watchlists
```

## API

```http
POST /api/auctions/{id}/watch
DELETE /api/auctions/{id}/watch
GET /api/users/me/watchlist
```

## Unique

```text
user_id + auction_id
```

## Frontend

Button:

```text
♡ Watch
♥ Watching
```

## Commit

```text
feat: implement auction watchlist
```

---

# 29. Chức năng 27 — Notification cơ bản

## Table

```text
notifications
```

## Types

```text
OUTBID
AUCTION_WON
AUCTION_LOST
AUCTION_STARTING
AUCTION_ENDING
```

## API

```http
GET /api/notifications
PATCH /api/notifications/{id}/read
PATCH /api/notifications/read-all
```

## Version đầu

Tạo notification trực tiếp trong backend service.

Kafka chưa cần.

## Frontend

Bell icon.

## Commit

```text
feat: implement user notifications
```

---

# PHASE 9 — ADVANCED AUCTION LOGIC

# 30. Chức năng 28 — Anti-Sniping

## Mục tiêu

Nếu bid gần giờ kết thúc thì kéo dài Auction.

Ví dụ:

```text
window = 30s
extension = 120s
```

Nếu:

```text
remaining <= 30s
```

sau bid hợp lệ:

```text
endTime += 120s
```

## WebSocket

Broadcast:

```text
AUCTION_EXTENDED
```

## Test

Bid còn:

```text
15s
```

Expected:

```text
endTime được tăng
```

## Commit

```text
feat: implement auction anti-sniping
```

---

# 31. Chức năng 29 — Auto Bid

## Table

```text
auto_bids
```

Fields:

```text
id
auction_id
user_id
max_amount
active
created_at
updated_at
```

## API

```http
POST /api/auctions/{id}/auto-bid
PUT /api/auctions/{id}/auto-bid
DELETE /api/auctions/{id}/auto-bid
```

## Logic cơ bản

User A:

```text
max = 20m
```

Current:

```text
10m
```

User B bid:

```text
11m
```

System có thể đặt bid cho A:

```text
11.5m
```

nếu còn <= max.

## Quan trọng

Auto Bid phải chạy trong transaction và tôn trọng locking.

Đừng làm Auto Bid trước khi Basic Bid + Locking đã hoàn thiện.

## Commit

```text
feat: implement automatic bidding
```

---

# PHASE 10 — PAYMENT

# 32. Chức năng 30 — Mock Payment

## Table

```text
payments
```

Fields:

```text
id
auction_id
user_id
amount
status
expired_at
created_at
updated_at
```

## Khi Winner được chọn

Tạo:

```text
Payment PENDING
```

## API

```http
GET /api/payments/{id}
POST /api/payments/{id}/pay
```

Demo cho phép chọn:

```text
SUCCESS
FAILED
```

## Rules

Chỉ Winner được trả payment đó.

## Commit

```text
feat: implement mock winner payment
```

---

# 33. Chức năng 31 — Order

## Table

```text
orders
```

## Khi Payment Success

Tạo / update:

```text
Order
status = PAID
```

Auction:

```text
ENDED → COMPLETED
```

## API

```http
GET /api/users/me/orders
GET /api/users/me/orders/{id}
```

## Commit

```text
feat: implement auction orders
```

---

# PHASE 11 — REDIS

# 34. Chức năng 32 — Redis Cache

## Chỉ thêm sau khi core đã chạy ổn

Cache:

```text
GET /api/auctions
GET /api/auctions/{id}
```

## Rule

Database vẫn là source of truth.

Khi bid thành công:

```text
invalidate auction detail cache
```

Không cache dữ liệu theo cách khiến bid bị stale.

## Commit

```text
feat: add Redis caching for auctions
```

---

# 35. Chức năng 33 — Rate Limiting

## Mục tiêu

Chống spam Place Bid.

Ví dụ:

```text
10 requests / 10 seconds / user
```

Redis key:

```text
rate:bid:{userId}
```

Nếu vượt:

```text
429 TOO MANY REQUESTS
```

## Commit

```text
feat: add Redis rate limiting for bidding
```

---

# PHASE 12 — EVENT DRIVEN

# 36. Chức năng 34 — Kafka Event

## Chỉ làm khi bid/payment core ổn định

Events:

```text
BidPlacedEvent
AuctionStartedEvent
AuctionEndedEvent
AuctionExtendedEvent
PaymentSucceededEvent
```

## Producer

Service publish event sau khi transaction thành công.

## Consumer đầu tiên

```text
Notification Consumer
```

Ví dụ:

```text
BidPlacedEvent
↓
find previous highest bidder
↓
create OUTBID notification
```

## Rule quan trọng

Kafka không quyết định bid có thành công hay không.

Database transaction quyết định.

## Commit

```text
feat: add Kafka auction events
```

---

# PHASE 13 — ADMIN & TRACEABILITY

# 37. Chức năng 35 — Audit Log

## Table

```text
audit_logs
```

Log:

```text
USER_LOGIN
AUCTION_CREATED
AUCTION_APPROVED
AUCTION_REJECTED
BID_PLACED
AUCTION_EXTENDED
AUCTION_ENDED
PAYMENT_SUCCESS
USER_BLOCKED
```

## Admin API

```http
GET /api/admin/audit-logs
```

## Frontend

```text
/admin/audit-logs
```

## Commit

```text
feat: implement audit logging
```

---

# PHASE 14 — TESTING

# 38. Chức năng 36 — Integration Test

## Dùng

```text
Spring Boot Test
Testcontainers
PostgreSQL
Redis
Kafka
```

## Test Flow

Ví dụ:

```text
Create Seller
↓
Create Product
↓
Create Auction
↓
Approve
↓
Buyer Bid
↓
End Auction
↓
Winner
↓
Payment
```

## Commit

```text
test: add integration tests for auction flow
```

---

# 39. Chức năng 37 — Concurrency Test

## Test quan trọng nhất

Setup:

```text
currentPrice = 20m
minimumIncrement = 1m
```

100 requests:

```text
21m
```

Expected:

```text
1 success
99 fail
```

Sau test:

```text
auction.currentPrice = 21m
```

Không được:

```text
100 successful bids
```

## Commit

```text
test: add concurrent bidding tests
```

---

# PHASE 15 — DEVOPS

# 40. Chức năng 38 — Docker Compose

Services:

```text
frontend
backend
postgres
redis
kafka
```

Một command:

```bash
docker compose up
```

phải chạy được project.

## Commit

```text
chore: dockerize NexBid application
```

---

# 41. Chức năng 39 — Load Test

Dùng:

```text
k6
```

Test:

```text
10 users
100 users
500 users
1000 users
```

Đặc biệt test endpoint:

```http
POST /api/auctions/{id}/bids
```

Đo:

```text
RPS
p50
p95
p99
error rate
```

Lưu kết quả trong:

```text
docs/load-test/
```

## Commit

```text
perf: add auction load testing
```

---

# 42. Chức năng 40 — README + Demo

README phải có:

```text
Project Overview
Screenshots
Tech Stack
Architecture
ERD
Auction Flow
Concurrent Bid Handling
Realtime Flow
Redis
Kafka
API Docs
Run Instructions
Testing
Load Test Results
```

Screenshot quan trọng:

```text
Home
Auction Detail
Realtime Bid
Seller Dashboard
Admin Approval
Swagger
Load Test Result
```

Có thể thêm GIF realtime bid.

## Commit

```text
docs: complete NexBid project documentation
```

---

# 43. Thứ tự màn hình Frontend nên làm

Không thiết kế toàn bộ UI ngay từ đầu.

Làm đúng thứ tự:

```text
01 /register
02 /login
03 /profile

04 /seller/products
05 /seller/products/create

06 /seller/auctions
07 /seller/auctions/create

08 /admin/auctions

09 /auctions
10 /auctions/[id]

11 /my-bids
12 /watchlist
13 /my-wins

14 /notifications
15 /payments
16 /orders

17 /admin/users
18 /admin/audit-logs
```

---

# 44. Thứ tự Database Migration

Tạo bảng theo thứ tự:

```text
01 users
02 roles
03 user_roles

04 categories

05 products
06 product_images

07 auctions

08 bids
09 auto_bids
10 watchlists

11 notifications

12 payments
13 orders

14 audit_logs
```

Không tạo tất cả một lần nếu đang học.

Tạo bảng nào khi tới chức năng đó.

---

# 45. Thứ tự học kỹ thuật trong project

Project này cũng nên được dùng như roadmap học.

## Giai đoạn 1

Học chắc:

```text
REST API
Controller
Service
Repository
DTO
Validation
Exception
JPA
Spring Security
JWT
```

## Giai đoạn 2

Sau khi Place Bid chạy:

```text
@Transactional
Database Locking
Race Condition
Isolation
Concurrency
```

## Giai đoạn 3

Sau khi concurrency ổn:

```text
WebSocket
Scheduler
```

## Giai đoạn 4

Sau core:

```text
Redis
Rate Limiting
Caching
```

## Giai đoạn 5

Sau Redis:

```text
Kafka
Event Driven
Retry
DLQ
```

## Giai đoạn 6

Cuối project:

```text
Docker
Integration Test
Load Test
Observability
```

---

# 46. Checklist trước khi chuyển sang chức năng tiếp theo

Mỗi chức năng phải trả lời YES cho tất cả:

```text
[ ] Database đúng chưa?
[ ] Validation đủ chưa?
[ ] Business Rule đúng chưa?
[ ] API chạy chưa?
[ ] Test case chính pass chưa?
[ ] Error case test chưa?
[ ] Permission đúng chưa?
[ ] Frontend gọi API được chưa?
[ ] Loading/Error UI có chưa?
[ ] Có commit Git chưa?
```

Nếu còn NO thì chưa chuyển bước.

---

# 47. Quy tắc không được phá

## Rule 1

Không thêm Redis trước khi basic Auction chạy ổn.

## Rule 2

Không thêm Kafka trước khi transaction Bid đúng.

## Rule 3

Không làm Auto Bid trước khi concurrent bidding đúng.

## Rule 4

Không làm payment trước khi winner selection đúng.

## Rule 5

Không làm microservices.

## Rule 6

Không để frontend quyết định nghiệp vụ quan trọng.

Frontend không quyết định:

```text
Current Price
Winner
Auction Ended
Bid Valid
Payment Owner
```

Backend quyết định toàn bộ.

---

# 48. Mốc kiểm tra project

## Milestone 1 — Auth Complete

Hoàn thành tới:

```text
Role Authorization
```

Demo:

```text
Register
Login
Protected API
```

---

## Milestone 2 — Seller Flow Complete

Hoàn thành tới:

```text
Admin Approve Auction
```

Demo:

```text
Seller Create Product
Seller Create Auction
Admin Approve
```

---

## Milestone 3 — Auction MVP

Hoàn thành tới:

```text
Winner Selection
```

Demo:

```text
Auction List
Auction Detail
Bid
Realtime
Auto Start
Auto End
Winner
```

Đây là MVP thật sự.

---

## Milestone 4 — Business Complete

Hoàn thành:

```text
Watchlist
Notification
Anti-Sniping
Auto Bid
Payment
Order
```

---

## Milestone 5 — Engineering Complete

Hoàn thành:

```text
Redis
Kafka
Audit
Integration Test
Concurrency Test
Docker
Load Test
```

Đây là version nên đưa CV/GitHub.

---

# 49. Thứ tự ưu tiên nếu không đủ thời gian

Nếu project bị thiếu thời gian, ưu tiên:

```text
MUST HAVE

Authentication
Product
Auction
Admin Approval
Place Bid
Concurrency Locking
WebSocket
Auto Start / End
Winner
Docker
README
```

Sau đó:

```text
SHOULD HAVE

Watchlist
Notification
Anti-Sniping
Payment
Order
Redis
```

Cuối cùng:

```text
NICE TO HAVE

Auto Bid
Kafka
Audit
Observability
Advanced Analytics
```

---

# 50. Cách làm project mỗi ngày

Mỗi buổi chỉ lấy 1 task.

Ví dụ:

## Ngày 1

```text
User Entity + Role
```

## Ngày 2

```text
Register API
```

## Ngày 3

```text
Login + JWT
```

## Ngày 4

```text
Role Authorization
```

## Ngày 5

```text
Product Entity + Create Product
```

Không bắt buộc đúng số ngày này. Ý chính là:

```text
1 session = 1 feature rõ ràng
```

---

# 51. Bước nên bắt đầu ngay

Bước đầu tiên:

```text
CHỨC NĂNG 01 — KHỞI TẠO PROJECT
```

Sau khi hoàn thành mới chuyển:

```text
CHỨC NĂNG 02 — API RESPONSE + EXCEPTION
```

Sau đó:

```text
CHỨC NĂNG 03 — USER + ROLE
```

Không cần nghĩ tới:

```text
Kafka
Redis
WebSocket
Auto Bid
```

ở thời điểm bắt đầu.

---

# 52. Kết luận

Roadmap đúng của NexBid là:

```text
Foundation
↓
Authentication
↓
Product
↓
Auction
↓
Basic Bid
↓
Concurrency
↓
Realtime
↓
Auction Lifecycle
↓
Advanced Business Logic
↓
Payment
↓
Redis
↓
Kafka
↓
Testing
↓
Docker
↓
Load Testing
↓
Portfolio
```

Nguyên tắc quan trọng nhất:

> Chỉ thêm công nghệ khi đã có một bài toán thực tế cần công nghệ đó giải quyết.

Ví dụ:

```text
Race Condition
→ Locking

Realtime Bid
→ WebSocket

Spam Bid
→ Redis Rate Limiting

Async Notification
→ Kafka

Environment Setup
→ Docker
```

Như vậy project vừa dễ học, vừa có lý do kiến trúc rõ ràng, vừa đủ chiều sâu để trình bày trên GitHub và khi phỏng vấn.
