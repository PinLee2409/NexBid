# ĐẶC TẢ DỰ ÁN — NEXBID
## Hệ thống đấu giá trực tuyến thời gian thực

**Tên dự án:** NexBid  
**Loại dự án:** Full-stack Web Application  
**Mục tiêu:** Portfolio cá nhân / GitHub Project  
**Kiến trúc đề xuất:** Modular Monolith  
**Frontend:** Next.js + TypeScript  
**Backend:** Java Spring Boot  
**Database:** PostgreSQL  
**Realtime:** WebSocket  
**Cache / Rate Limit:** Redis  
**Message Broker:** Kafka  
**Triển khai:** Docker / Docker Compose  

---

# 1. Tổng quan dự án

NexBid là một nền tảng đấu giá trực tuyến cho phép người bán đăng sản phẩm để đấu giá và người mua tham gia trả giá theo thời gian thực.

Một phiên đấu giá có thời gian bắt đầu, thời gian kết thúc, giá khởi điểm và bước giá tối thiểu. Trong thời gian phiên đấu giá đang hoạt động, nhiều người dùng có thể cùng lúc đặt giá. Hệ thống phải đảm bảo chỉ những lượt trả giá hợp lệ được ghi nhận và không xảy ra lỗi dữ liệu khi nhiều người đặt giá đồng thời.

Khi phiên đấu giá kết thúc, người trả giá hợp lệ cao nhất trở thành người chiến thắng và có thể tiến hành thanh toán.

Project tập trung vào các bài toán kỹ thuật thực tế:

- Đấu giá thời gian thực.
- Xử lý nhiều request bid đồng thời.
- Ngăn race condition và double update.
- WebSocket cập nhật giá trực tiếp.
- Redis cache và rate limiting.
- Kafka xử lý event bất đồng bộ.
- Transaction và locking.
- Auto close auction.
- Anti-sniping.
- Auto Bid.
- Payment mock.
- Notification.
- Audit log.
- Integration Test và Concurrency Test.

---

# 2. Mục tiêu dự án

## 2.1. Mục tiêu nghiệp vụ

Hệ thống cho phép:

1. Người dùng đăng ký và đăng nhập.
2. Người bán đăng sản phẩm đấu giá.
3. Admin duyệt sản phẩm trước khi mở đấu giá.
4. Người mua xem các phiên đấu giá đang diễn ra.
5. Người mua đặt giá trong thời gian thực.
6. Hệ thống xác định người thắng khi phiên đấu giá kết thúc.
7. Người thắng thực hiện thanh toán.
8. Người dùng theo dõi lịch sử đấu giá, lượt bid và sản phẩm quan tâm.
9. Hệ thống gửi thông báo khi:
   - bị người khác vượt giá;
   - đấu giá sắp kết thúc;
   - thắng đấu giá;
   - thanh toán thành công;
   - phiên đấu giá bị hủy.

## 2.2. Mục tiêu kỹ thuật

Project phải thể hiện được:

- RESTful API.
- Spring Security.
- JWT Authentication.
- Role Based Access Control.
- JPA / Hibernate.
- Database Transaction.
- Optimistic hoặc Pessimistic Locking.
- Concurrency Control.
- WebSocket.
- Redis.
- Rate Limiting.
- Kafka.
- Event-driven processing.
- Scheduler.
- Docker.
- Unit Test.
- Integration Test.
- Concurrency Test.
- Swagger / OpenAPI.
- Logging và Audit Trail.

---

# 3. Phạm vi dự án

Project được xây dựng cho một developer nên ưu tiên chiều sâu nghiệp vụ thay vì số lượng chức năng.

## 3.1. Chức năng nằm trong phạm vi

- Authentication.
- User Profile.
- Seller Product Management.
- Auction Management.
- Admin Approval.
- Auction Listing.
- Auction Detail.
- Real-time Bidding.
- Bid History.
- Watchlist.
- Anti-sniping.
- Auto Bid.
- Auction Closing.
- Winner Selection.
- Mock Payment.
- Order.
- Notification.
- Audit Log.
- Redis Cache.
- Rate Limiting.
- Kafka Event.
- WebSocket.
- Docker.

## 3.2. Chức năng chưa cần làm ở phiên bản đầu

- Thanh toán tiền thật.
- KYC.
- Shipping thực tế.
- Multi-currency.
- Microservices.
- Kubernetes.
- Elasticsearch.
- Recommendation AI.
- Mobile App.
- Chat trực tiếp giữa Buyer và Seller.

---

# 4. Vai trò người dùng

Hệ thống có 3 role chính.

## 4.1. BUYER

Người mua có thể:

- Xem danh sách phiên đấu giá.
- Xem chi tiết sản phẩm.
- Theo dõi phiên đấu giá.
- Đặt giá.
- Sử dụng Auto Bid.
- Xem lịch sử bid.
- Xem các phiên đã thắng.
- Thanh toán đơn thắng.
- Xem thông báo.

## 4.2. SELLER

Người bán có thể:

- Tạo sản phẩm.
- Upload hình ảnh.
- Tạo phiên đấu giá.
- Thiết lập:
  - giá khởi điểm;
  - bước giá tối thiểu;
  - thời gian bắt đầu;
  - thời gian kết thúc;
  - anti-sniping.
- Gửi phiên đấu giá chờ Admin duyệt.
- Theo dõi lượt bid.
- Xem kết quả phiên đấu giá.

Seller không được phép bid vào chính sản phẩm của mình.

## 4.3. ADMIN

Admin có thể:

- Xem danh sách phiên đấu giá chờ duyệt.
- Approve auction.
- Reject auction.
- Hủy auction trong trường hợp vi phạm.
- Xem user.
- Khóa user.
- Xem audit log.
- Theo dõi các giao dịch bất thường.

---

# 5. Trạng thái chính

## 5.1. Auction Status

```text
DRAFT
  ↓
PENDING_APPROVAL
  ↓
SCHEDULED
  ↓
ACTIVE
  ↓
ENDED
  ↓
COMPLETED
```

Các trạng thái khác:

```text
REJECTED
CANCELLED
```

### Ý nghĩa

| Trạng thái | Ý nghĩa |
|---|---|
| DRAFT | Seller đang tạo auction |
| PENDING_APPROVAL | Đã gửi Admin duyệt |
| SCHEDULED | Đã duyệt nhưng chưa tới giờ bắt đầu |
| ACTIVE | Đang đấu giá |
| ENDED | Đã hết thời gian đấu giá |
| COMPLETED | Winner đã thanh toán thành công |
| REJECTED | Admin từ chối |
| CANCELLED | Auction bị hủy |

---

## 5.2. Payment Status

```text
PENDING
SUCCESS
FAILED
EXPIRED
REFUNDED
```

---

## 5.3. Order Status

```text
PENDING_PAYMENT
PAID
PROCESSING
COMPLETED
CANCELLED
```

---

# 6. Luồng nghiệp vụ tổng quát

```text
Seller
  ↓
Create Product
  ↓
Create Auction
  ↓
Submit for Approval
  ↓
Admin Approve
  ↓
SCHEDULED
  ↓
Start Time
  ↓
ACTIVE
  ↓
Buyer Bid
  ↓
Realtime Price Update
  ↓
End Time
  ↓
Select Winner
  ↓
Create Payment
  ↓
Payment Success
  ↓
Create / Complete Order
```

---

# 7. Đặc tả chức năng

# 7.1. Đăng ký

## Mô tả

Người dùng tạo tài khoản mới.

## Input

- Full name.
- Email.
- Password.

## Business Rules

- Email phải duy nhất.
- Password tối thiểu 8 ký tự.
- Email hợp lệ.
- Password được mã hóa bằng BCrypt.
- Role mặc định là BUYER.
- User có thể đăng ký Seller sau này hoặc hệ thống có thể cho phép chọn role khi tạo tài khoản ở môi trường demo.

---

# 7.2. Đăng nhập

## Input

- Email.
- Password.

## Output

- Access Token.
- Refresh Token nếu triển khai.
- User Information.

## Business Rules

- Tài khoản bị khóa không được đăng nhập.
- Password sai trả về lỗi chung, không tiết lộ email có tồn tại hay không.

---

# 7.3. Quản lý sản phẩm

Seller có thể tạo sản phẩm gồm:

- Name.
- Description.
- Category.
- Condition.
- Images.

## Product Condition

Ví dụ:

```text
NEW
LIKE_NEW
GOOD
FAIR
USED
```

Một product có thể có nhiều ảnh.

---

# 7.4. Tạo Auction

Seller chọn một product và nhập:

- Starting Price.
- Minimum Bid Increment.
- Start Time.
- End Time.
- Anti Sniping Enabled.
- Anti Sniping Window.
- Extension Duration.

Ví dụ:

```text
Starting Price: 10,000,000
Minimum Increment: 500,000
Start Time: 2026-10-01 20:00
End Time: 2026-10-01 21:00
Anti Sniping Window: 30 seconds
Extension: 120 seconds
```

## Business Rules

- End Time phải lớn hơn Start Time.
- Starting Price > 0.
- Minimum Increment > 0.
- Seller phải là chủ sản phẩm.
- Auction chỉ được chỉnh sửa khi còn ở DRAFT.
- Sau khi gửi duyệt thì Seller không được chỉnh sửa trực tiếp.

---

# 7.5. Admin duyệt Auction

Admin xem auction ở trạng thái:

```text
PENDING_APPROVAL
```

Admin có thể:

```text
APPROVE
REJECT
```

Nếu approve:

- Nếu chưa đến startTime → SCHEDULED.
- Nếu startTime đã tới → ACTIVE.

Nếu reject:

```text
status = REJECTED
rejectionReason = "..."
```

---

# 7.6. Danh sách Auction

User có thể xem:

- Auction đang diễn ra.
- Auction sắp diễn ra.
- Auction đã kết thúc.

Filter:

- Category.
- Price.
- Status.
- Ending Soon.
- Most Bids.
- Newly Listed.

Sort:

```text
ENDING_SOON
NEWEST
PRICE_ASC
PRICE_DESC
MOST_BIDS
```

---

# 7.7. Chi tiết Auction

Trang chi tiết hiển thị:

- Product Image.
- Product Name.
- Description.
- Seller.
- Current Price.
- Starting Price.
- Minimum Increment.
- Bid Count.
- Countdown.
- Bid History.
- Watch Button.
- Place Bid.
- Auto Bid.
- Auction Status.

Ví dụ:

```text
MacBook Pro M3

Current Bid:
32,500,000 VND

Minimum next bid:
33,000,000 VND

Ends in:
00:08:21

[ 33,000,000 ]
[ PLACE BID ]
```

---

# 7.8. Place Bid

Đây là chức năng quan trọng nhất của hệ thống.

## Request

```http
POST /api/auctions/{auctionId}/bids
```

Request body:

```json
{
  "amount": 18000000
}
```

## Validation Flow

Backend phải kiểm tra:

```text
Auction tồn tại?
        ↓
Auction ACTIVE?
        ↓
Current Server Time < End Time?
        ↓
Buyer != Seller?
        ↓
User không bị khóa?
        ↓
Bid Amount hợp lệ?
        ↓
Lock Auction
        ↓
Re-check Current Price
        ↓
Save Bid
        ↓
Update Auction Current Price
        ↓
Commit Transaction
        ↓
Publish WebSocket Event
        ↓
Publish Kafka Event
```

---

# 8. Quy tắc Bid

Giả sử:

```text
Current Price = 10,000,000
Minimum Increment = 500,000
```

Minimum Bid:

```text
10,500,000
```

Các bid:

```text
10,100,000 → INVALID
10,499,999 → INVALID
10,500,000 → VALID
11,000,000 → VALID
```

Nếu chưa có bid:

```text
Minimum Bid = Starting Price
```

Nếu đã có bid:

```text
Minimum Bid = Current Price + Minimum Increment
```

---

# 9. Xử lý Concurrent Bid

Đây là yêu cầu kỹ thuật bắt buộc.

Ví dụ:

```text
Current Price = 20,000,000
```

Hai request đến gần như cùng lúc:

```text
User A → 21,000,000
User B → 21,000,000
```

Hệ thống không được cho cả hai cùng SUCCESS.

## Giải pháp đề xuất

### Option A — Pessimistic Locking

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
```

Khi xử lý bid:

```text
Transaction A lock Auction
        ↓
Validate
        ↓
Update Price
        ↓
Commit
        ↓
Release Lock

Transaction B được xử lý sau
        ↓
Re-read Current Price
        ↓
Bid không còn hợp lệ
        ↓
Reject
```

### Option B — Optimistic Locking

Auction có:

```java
@Version
private Long version;
```

Nếu hai transaction update cùng version:

- Một transaction thành công.
- Transaction còn lại nhận OptimisticLockException.
- Backend có thể trả về:

```text
409 CONFLICT
```

## Khuyến nghị

Version đầu tiên sử dụng:

```text
PESSIMISTIC_WRITE
```

để logic dễ hiểu và dễ kiểm thử.

Sau đó có thể benchmark với Optimistic Locking.

---

# 10. WebSocket Realtime

Khi một bid thành công, server broadcast event.

Ví dụ channel:

```text
/topic/auctions/{auctionId}
```

Event:

```json
{
  "type": "BID_PLACED",
  "auctionId": 123,
  "currentPrice": 18000000,
  "totalBids": 37,
  "bidder": "pin***",
  "createdAt": "2026-10-01T20:15:22"
}
```

Frontend nhận event và cập nhật:

- Current Price.
- Bid Count.
- Bid History.
- Minimum Next Bid.
- Notification "You've been outbid".

Không reload trang.

---

# 11. Countdown

Countdown hiển thị ở frontend nhưng thời gian thật phải do server kiểm soát.

Frontend không có quyền quyết định auction đã kết thúc.

Nguồn chính xác:

```text
auction.endTime
```

và:

```text
serverCurrentTime
```

Khi frontend load page, API trả:

```json
{
  "serverTime": "...",
  "endTime": "..."
}
```

Frontend dùng hai giá trị này để render countdown.

---

# 12. Auction Scheduler

Backend chạy scheduler để:

- Chuyển SCHEDULED → ACTIVE.
- Chuyển ACTIVE → ENDED.
- Xác định Winner.
- Tạo Payment Pending.
- Publish AuctionEndedEvent.

Ví dụ:

```text
Every 5 seconds
```

hoặc sử dụng job scheduler phù hợp.

Logic:

```text
Find auction:
status = ACTIVE
AND endTime <= now
        ↓
Lock Auction
        ↓
Find Highest Valid Bid
        ↓
Set Winner
        ↓
status = ENDED
        ↓
Create Pending Payment
        ↓
Publish AuctionEndedEvent
```

---

# 13. Anti-Sniping

Anti-sniping giúp tránh việc người dùng đợi giây cuối để đặt giá.

Ví dụ cấu hình:

```text
End Time = 21:00:00

Anti Snipe Window = 30 sec
Extension = 120 sec
```

Nếu user bid lúc:

```text
20:59:45
```

thì:

```text
remainingTime = 15 sec
```

vì nhỏ hơn 30 sec nên:

```text
New End Time = 21:02:00
```

## Rule

```text
IF
auction.antiSnipingEnabled = true
AND
remainingTime <= antiSnipingWindow

THEN
endTime = endTime + extensionDuration
```

Sau khi extension:

- WebSocket gửi AUCTION_EXTENDED.
- Frontend cập nhật countdown.

---

# 14. Auto Bid

Auto Bid cho phép Buyer nhập số tiền tối đa mà họ chấp nhận trả.

Ví dụ:

```text
Current Price = 10,000,000
Minimum Increment = 500,000

User A Max Bid = 20,000,000
```

User B bid:

```text
11,000,000
```

System tự bid cho A:

```text
11,500,000
```

User B bid:

```text
15,000,000
```

System:

```text
15,500,000
```

cho tới khi vượt Max Bid của A.

## Bảng auto_bids

```text
id
auction_id
user_id
max_amount
created_at
updated_at
```

## Business Rules

- Max Amount phải >= Minimum Next Bid.
- Một user chỉ có một Auto Bid active trên một auction.
- Có thể update Max Amount.
- Giá Max Amount không hiển thị cho user khác.

---

# 15. Watchlist

Buyer có thể theo dõi auction.

API:

```http
POST /api/auctions/{id}/watch
DELETE /api/auctions/{id}/watch
```

User có page:

```text
My Watchlist
```

Có thể gửi notification khi:

- Auction sắp bắt đầu.
- Auction sắp kết thúc.
- Auction có bid mới.

---

# 16. Auction Winner

Khi auction kết thúc:

Nếu không có bid:

```text
winner = null
```

Auction vẫn:

```text
ENDED
```

Nếu có bid:

```text
winner = highestBid.user
finalPrice = highestBid.amount
```

Sau đó tạo:

```text
Payment
```

với trạng thái:

```text
PENDING
```

---

# 17. Mock Payment

Project không cần tiền thật ở version đầu.

Winner có thể chọn:

```text
PAY SUCCESS
PAY FAILED
```

dùng cho demo.

## Flow

```text
Winner
  ↓
Payment Pending
  ↓
POST /payments/{id}/pay
  ↓
SUCCESS
  ↓
Order PAID
  ↓
Auction COMPLETED
```

Nếu payment hết hạn:

```text
Payment → EXPIRED
```

Version nâng cao có thể offer cho người trả giá cao thứ hai.

---

# 18. Notification

Các loại Notification:

```text
OUTBID
AUCTION_STARTING
AUCTION_ENDING
AUCTION_WON
AUCTION_LOST
AUCTION_EXTENDED
PAYMENT_REQUIRED
PAYMENT_SUCCESS
PAYMENT_EXPIRED
AUCTION_CANCELLED
```

Notification được lưu DB.

Frontend có:

```text
Bell Icon
```

và WebSocket notification channel.

---

# 19. Kafka Event

Kafka không nằm trên critical path của Place Bid.

Bid thành công phải được commit DB trước.

Sau đó publish event.

Ví dụ:

```text
BidPlacedEvent
AuctionStartedEvent
AuctionEndedEvent
AuctionExtendedEvent
PaymentSucceededEvent
```

Consumer:

```text
Notification Consumer
Analytics Consumer
Audit Consumer
```

Kiến trúc:

```text
Auction Service
      ↓
    Kafka
   ↙     ↘
Notification Analytics
```

---

# 20. Redis

Redis được sử dụng cho các use case cụ thể.

## 20.1. Cache

Cache:

- Auction detail.
- Active auction list.
- Trending auctions.

## 20.2. Rate Limiting

Ví dụ:

```text
Maximum:
10 bid requests / 10 seconds / user
```

Nếu vượt:

```text
429 TOO MANY REQUESTS
```

## 20.3. Online Viewer Count

Key ví dụ:

```text
auction:viewers:123
```

## 20.4. Temporary State

Có thể dùng để lưu:

- Short-lived auction data.
- WebSocket session metadata.

Database vẫn là source of truth cho bid và winner.

---

# 21. Audit Log

Các hành động quan trọng phải được ghi lại.

Ví dụ:

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

Audit Log fields:

```text
id
user_id
action
entity_type
entity_id
old_value
new_value
ip_address
created_at
```

---

# 22. Database Design

Các bảng chính:

```text
users
roles
user_roles

categories

products
product_images

auctions

bids
auto_bids

watchlists

payments
orders

notifications

audit_logs
```

---

# 23. Database Schema sơ bộ

## users

```text
id
full_name
email
password
status
created_at
updated_at
```

---

## roles

```text
id
name
```

Ví dụ:

```text
BUYER
SELLER
ADMIN
```

---

## products

```text
id
seller_id
category_id
name
description
condition
status
created_at
updated_at
```

---

## product_images

```text
id
product_id
image_url
sort_order
```

---

## auctions

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

winner_id

anti_sniping_enabled
anti_sniping_window_seconds
extension_seconds

version

created_at
updated_at
```

---

## bids

```text
id
auction_id
bidder_id
amount
created_at
```

Index bắt buộc:

```text
auction_id
bidder_id
created_at
```

---

## auto_bids

```text
id
auction_id
user_id
max_amount
active
created_at
updated_at
```

Unique:

```text
auction_id + user_id
```

---

## watchlists

```text
id
user_id
auction_id
created_at
```

Unique:

```text
user_id + auction_id
```

---

## payments

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

---

## orders

```text
id
auction_id
buyer_id
seller_id
payment_id
amount
status
created_at
updated_at
```

---

## notifications

```text
id
user_id
type
title
message
is_read
created_at
```

---

# 24. ERD đơn giản

```text
User
 ├── Products
 ├── Bids
 ├── AutoBids
 ├── Watchlists
 ├── Notifications
 └── Orders

Category
 └── Products

Product
 ├── ProductImages
 └── Auction

Auction
 ├── Bids
 ├── AutoBids
 ├── Watchlists
 ├── Payment
 └── Order
```

---

# 25. Backend Module Structure

Đề xuất sử dụng Modular Monolith.

```text
src/main/java/com/nexbid

├── auth
│   ├── controller
│   ├── service
│   ├── dto
│   └── security
│
├── user
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   └── dto
│
├── product
│
├── auction
│   ├── controller
│   ├── service
│   ├── repository
│   ├── entity
│   ├── dto
│   ├── scheduler
│   └── websocket
│
├── bid
│
├── payment
│
├── notification
│
├── audit
│
├── infrastructure
│   ├── redis
│   ├── kafka
│   ├── websocket
│   └── config
│
└── common
    ├── exception
    ├── response
    ├── validation
    └── util
```

---

# 26. Frontend Structure

```text
src/

├── app
│   ├── login
│   ├── register
│   ├── auctions
│   │   ├── page.tsx
│   │   └── [id]
│   │       └── page.tsx
│   ├── seller
│   ├── admin
│   ├── profile
│   └── watchlist
│
├── components
│   ├── auction
│   ├── bid
│   ├── product
│   ├── layout
│   └── ui
│
├── services
│   ├── auth.ts
│   ├── auction.ts
│   ├── bid.ts
│   └── websocket.ts
│
├── hooks
├── types
├── utils
└── store
```

---

# 27. API Design

## Authentication

```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
```

---

## User

```http
GET /api/users/me
PUT /api/users/me
GET /api/users/me/bids
GET /api/users/me/wins
GET /api/users/me/watchlist
```

---

## Products

```http
GET    /api/products/{id}
POST   /api/products
PUT    /api/products/{id}
DELETE /api/products/{id}
```

---

## Auctions

```http
GET  /api/auctions
GET  /api/auctions/{id}

POST /api/auctions
PUT  /api/auctions/{id}

POST /api/auctions/{id}/submit
```

---

## Bid

```http
POST /api/auctions/{id}/bids
GET  /api/auctions/{id}/bids
```

---

## Auto Bid

```http
POST   /api/auctions/{id}/auto-bid
PUT    /api/auctions/{id}/auto-bid
DELETE /api/auctions/{id}/auto-bid
```

---

## Watchlist

```http
POST   /api/auctions/{id}/watch
DELETE /api/auctions/{id}/watch
```

---

## Payment

```http
GET  /api/payments/{id}
POST /api/payments/{id}/pay
```

---

## Notification

```http
GET  /api/notifications
PATCH /api/notifications/{id}/read
PATCH /api/notifications/read-all
```

---

## Admin

```http
GET  /api/admin/auctions/pending

POST /api/admin/auctions/{id}/approve
POST /api/admin/auctions/{id}/reject
POST /api/admin/auctions/{id}/cancel

GET  /api/admin/users
PATCH /api/admin/users/{id}/block

GET /api/admin/audit-logs
```

---

# 28. Chuẩn Response

Response thành công:

```json
{
  "success": true,
  "message": "Bid placed successfully",
  "data": {
    "auctionId": 123,
    "currentPrice": 18000000
  }
}
```

Response lỗi:

```json
{
  "success": false,
  "code": "BID_TOO_LOW",
  "message": "Bid amount must be at least 18,500,000",
  "timestamp": "..."
}
```

---

# 29. Error Code đề xuất

```text
USER_NOT_FOUND
EMAIL_ALREADY_EXISTS
INVALID_CREDENTIALS
ACCOUNT_BLOCKED

PRODUCT_NOT_FOUND

AUCTION_NOT_FOUND
AUCTION_NOT_ACTIVE
AUCTION_ALREADY_ENDED
AUCTION_NOT_EDITABLE

SELLER_CANNOT_BID

BID_TOO_LOW
BID_CONFLICT
BID_RATE_LIMITED

AUTO_BID_INVALID

PAYMENT_NOT_FOUND
PAYMENT_EXPIRED

ACCESS_DENIED
```

---

# 30. Security

- JWT Authentication.
- BCrypt Password.
- RBAC.
- Validate ownership.
- Validate input.
- Rate limit bid API.
- Không expose Auto Bid max amount.
- Không cho Seller bid sản phẩm của mình.
- Không tin dữ liệu Current Price từ frontend.
- Server quyết định auction status.
- Server quyết định winner.
- Log các hành động nhạy cảm.

---

# 31. Non-functional Requirements

## Performance

Mục tiêu ban đầu:

```text
Auction List:
p95 < 500ms

Auction Detail:
p95 < 300ms

Place Bid:
p95 < 500ms
```

Không cần cam kết tuyệt đối ở môi trường dev, nhưng phải có benchmark.

## Concurrency

Hệ thống phải xử lý đúng khi nhiều user bid cùng auction.

Yêu cầu:

```text
Không có 2 bid cùng được xem là winning bid
nếu chúng xung đột về cùng một current price.
```

## Reliability

Bid đã báo SUCCESS phải tồn tại trong database.

## Consistency

Database là source of truth.

Redis chỉ là cache / temporary storage.

---

# 32. Testing

## Unit Test

Các case bắt buộc:

- Bid hợp lệ.
- Bid thấp hơn minimum.
- Seller bid sản phẩm của mình.
- Auction chưa bắt đầu.
- Auction đã kết thúc.
- Anti-sniping.
- Auto Bid.
- Winner selection.

---

## Integration Test

Sử dụng:

```text
Spring Boot Test
Testcontainers
PostgreSQL Container
Redis Container
Kafka Container
```

---

## Concurrency Test

Case quan trọng:

```text
Current Price = 20m
Minimum Increment = 1m
```

100 thread cùng gửi:

```text
21m
```

Expected:

```text
1 bid success
99 bid fail / conflict
```

Database:

```text
currentPrice = 21m
```

---

# 33. Load Test

Có thể sử dụng:

```text
k6
JMeter
```

Benchmark:

```text
10 concurrent users
100 concurrent users
500 concurrent users
1000 concurrent users
```

Đo:

- Requests per second.
- p50 latency.
- p95 latency.
- p99 latency.
- Error rate.
- DB connection count.
- Redis hit rate.

---

# 34. Logging và Observability

Version nâng cao có thể thêm:

```text
OpenTelemetry
Prometheus
Grafana
```

Metrics:

```text
bid_requests_total
bid_success_total
bid_failed_total

active_auctions
websocket_connections

bid_latency
payment_success_total
```

---

# 35. Docker

Docker Compose gồm:

```text
frontend
backend
postgres
redis
kafka
```

Version nâng cao:

```text
prometheus
grafana
```

---

# 36. Trang giao diện

## Public

```text
/
 /auctions
 /auctions/{id}
 /login
 /register
```

## Buyer

```text
/profile
/my-bids
/my-wins
/watchlist
/notifications
/payments
```

## Seller

```text
/seller/dashboard
/seller/products
/seller/products/create
/seller/auctions
/seller/auctions/create
```

## Admin

```text
/admin
/admin/auctions
/admin/users
/admin/audit-logs
```

---

# 37. UI Trang Auction Detail

```text
┌───────────────────────────────────────┐
│                                       │
│        PRODUCT IMAGE                  │
│                                       │
├───────────────────────────────────────┤
│ MacBook Pro M3                        │
│                                       │
│ Current Bid                           │
│ 32,500,000 VND                        │
│                                       │
│ 48 bids                               │
│                                       │
│ Ends in                               │
│ 00 : 08 : 21                          │
│                                       │
│ Minimum next bid                      │
│ 33,000,000 VND                        │
│                                       │
│ [ 33,000,000 ]                        │
│ [ PLACE BID ]                         │
│                                       │
│ [ SET AUTO BID ]                      │
└───────────────────────────────────────┘

Bid History

pin***     32,500,000
alex***    32,000,000
john***    31,500,000
```

---

# 38. Notification Realtime

Ví dụ:

```text
You've been outbid!

Current Bid:
18,500,000 VND

[ BID AGAIN ]
```

Hoặc:

```text
Congratulations!

You won:
MacBook Pro M3

Winning Bid:
32,500,000 VND

[ PAY NOW ]
```

---

# 39. Roadmap phát triển

## Phase 1 — Core

- Project setup.
- Authentication.
- User.
- Product.
- Auction.
- Admin approval.
- Basic Bid.

Mục tiêu:

```text
Có thể tạo và tham gia auction.
```

---

## Phase 2 — Concurrency

- Transaction.
- Pessimistic Locking.
- Bid validation.
- Concurrency Test.

Mục tiêu:

```text
Không xảy ra race condition khi bid.
```

---

## Phase 3 — Realtime

- WebSocket.
- Realtime price.
- Realtime bid history.
- Countdown sync.

---

## Phase 4 — Auction Engine

- Scheduler.
- Auto start.
- Auto end.
- Winner selection.
- Anti-sniping.

---

## Phase 5 — Redis

- Cache.
- Rate limit.
- Viewer count.

---

## Phase 6 — Payment

- Mock Payment.
- Payment expiration.
- Order.
- Winner flow.

Sau Phase 6 project đã đủ tốt để đưa lên GitHub portfolio.

---

## Phase 7 — Advanced Bidding

- Auto Bid.
- Outbid notification.
- Watchlist.

---

## Phase 8 — Event Driven

- Kafka.
- Notification Consumer.
- Analytics Event.
- Retry.
- Dead Letter Queue nếu cần.

---

## Phase 9 — Production Quality

- Docker.
- Integration Test.
- Load Test.
- Swagger.
- Logging.
- Metrics.
- CI/CD.

---

# 40. GitHub README cần có

Repository khi hoàn thành nên có:

```text
README.md
docs/
docker-compose.yml
frontend/
backend/
```

README nên trình bày:

1. Project Overview.
2. Features.
3. Demo.
4. Screenshots.
5. Architecture.
6. Tech Stack.
7. ERD.
8. Auction Flow.
9. Concurrent Bid Handling.
10. WebSocket Flow.
11. Kafka Flow.
12. Installation.
13. API Documentation.
14. Testing.
15. Load Test Results.

---

# 41. Điểm nổi bật để trình bày trong Portfolio

Không nên chỉ viết:

> Built an online auction website.

Nên mô tả:

> Built a real-time auction platform using Spring Boot and Next.js with WebSocket-based live bidding, PostgreSQL transactional consistency, pessimistic locking for concurrent bid handling, Redis rate limiting and caching, Kafka event processing, automated auction lifecycle management and containerized deployment.

Các điểm đáng nhấn mạnh:

- Real-time Bidding.
- Race Condition Handling.
- Transactional Consistency.
- Pessimistic Locking.
- WebSocket.
- Redis.
- Kafka.
- Anti-sniping.
- Auto Bid.
- Automated Auction Closing.
- Concurrency Test.
- Load Test.

---

# 42. Điều kiện hoàn thành MVP

MVP được xem là hoàn thành khi:

- User đăng ký / đăng nhập được.
- Seller tạo sản phẩm.
- Seller tạo auction.
- Admin approve auction.
- Auction tự chuyển ACTIVE.
- Buyer đặt bid.
- Backend validate bid.
- Nhiều user bid cùng lúc không làm sai dữ liệu.
- Giá cập nhật realtime bằng WebSocket.
- Auction tự kết thúc.
- Winner được xác định đúng.
- Winner thanh toán mock.
- Auction chuyển COMPLETED.
- Có Swagger.
- Có Docker Compose.
- Có test cho logic Bid.

---

# 43. Điều kiện hoàn thành Portfolio Version

Portfolio Version gồm toàn bộ MVP và thêm:

- Redis Cache.
- Rate Limit.
- Anti-sniping.
- Auto Bid.
- Watchlist.
- Notification.
- Kafka.
- Audit Log.
- Integration Test.
- Concurrency Test.
- Load Test Result.
- README đầy đủ.
- Screenshot / GIF demo.
- Deploy demo nếu có thể.

---

# 44. Nguyên tắc kiến trúc

## Không microservice từ đầu

Backend sử dụng:

```text
Spring Boot Modular Monolith
```

Các module tách rõ ràng nhưng cùng một application.

Lý do:

- Dễ phát triển cá nhân.
- Dễ debug.
- Dễ transaction.
- Không tăng độ phức tạp không cần thiết.
- Vẫn thể hiện được kiến trúc tốt.

Nếu project phát triển lớn hơn, Notification hoặc Analytics có thể được tách ra sau.

---

# 45. Kiến trúc tổng quát

```text
                    ┌─────────────────┐
                    │     Next.js     │
                    │    Frontend     │
                    └────────┬────────┘
                             │
                       REST / WebSocket
                             │
                    ┌────────▼────────┐
                    │   Spring Boot   │
                    │ Modular Monolith│
                    └───┬─────┬───────┘
                        │     │
              ┌─────────▼┐   ┌▼──────────┐
              │PostgreSQL│   │   Redis   │
              └──────────┘   └───────────┘
                        │
                    ┌───▼───┐
                    │ Kafka │
                    └───┬───┘
                        │
             ┌──────────▼──────────┐
             │ Async Event Consumer │
             └─────────────────────┘
```

---

# 46. Nguyên tắc quan trọng nhất

Trong toàn bộ hệ thống:

```text
Frontend không quyết định giá.
Frontend không quyết định winner.
Frontend không quyết định auction đã kết thúc.
Redis không phải source of truth.
WebSocket không phải source of truth.
Kafka không phải nơi xác nhận bid.
```

Nguồn dữ liệu chính:

```text
PostgreSQL
```

Bid hợp lệ phải được xử lý trong:

```text
Database Transaction
```

sau đó mới:

```text
WebSocket broadcast
Kafka publish
Cache update
```

---

# 47. Kết luận

NexBid là project tập trung vào một bài toán nghiệp vụ dễ hiểu nhưng có chiều sâu kỹ thuật cao.

Điểm chính không nằm ở số lượng màn hình mà nằm ở việc xử lý đúng các bài toán:

```text
Realtime
Concurrency
Race Condition
Transaction
Locking
Caching
Rate Limiting
Event Driven
Scheduling
Testing
```

Mục tiêu cuối cùng là tạo ra một project cá nhân có thể dùng để:

- Học Spring Boot nâng cao.
- Học Next.js.
- Học kiến trúc backend.
- Làm portfolio GitHub.
- Demo trong CV.
- Trình bày khi phỏng vấn Backend / Full-stack Developer.
