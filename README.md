# NexBid

Real-time online auction platform. Sellers list lots, buyers bid against a
server-owned clock, and the server decides the close — minimum increments,
anti-sniping extensions, one winner per lot.

---

## Status

| | |
| --- | --- |
| Frontend | Complete, running on mock data |
| Backend | Function 32 of 40 — accounts, products, auctions and bidding, safe under concurrent load. Realtime price updates over WebSocket. Lots open and close on schedule, last-second bids extend the close, auto bids answer for their owners, and the top bidder wins. Watchlists, notifications, and a mock payment for the winner that completes the sale as an order. Lot details cached in Redis |

The frontend does not call the backend yet. The mock services mirror the REST
contract, so switching to the real API changes service bodies and nothing else.

---

## Stack

**Frontend** — Next.js 16, React 19, TypeScript, Tailwind CSS v4, shadcn/ui,
next-intl (EN/VI), next-themes.

**Backend** — Spring Boot 4.1, Java 21, Spring Data JPA, Spring Security,
WebSocket, PostgreSQL 17, Redis 7.

---

## Run

```bash
docker compose -f docker/compose.yaml up -d
```

```bash
cd backend && ./mvnw spring-boot:run
```

```bash
cd frontend && npm install && npm run dev
```

Frontend at http://localhost:3000 — works without the backend running.
Backend at http://localhost:8080/api/health

Postgres is published on port **55432** and Redis on **56379**, not the defaults — see
[`docker/compose.yaml`](docker/compose.yaml). Redis is only a cache: the backend keeps
working without it.

---

## API

| | |
| --- | --- |
| `POST /api/auth/register` | Public. Creates a BUYER. |
| `POST /api/auth/login` | Public. Returns a JWT valid for 2 hours. |
| `GET /api/users/me` | The signed-in account. |
| `PUT /api/users/me` | Renames it. Nothing else about an account is editable by its owner. |
| `GET /api/health` | Liveness. No token needed. |

Everything else requires `Authorization: Bearer <token>`. `/api/admin/**` needs the
ADMIN role and `/api/seller/**` needs SELLER; the frontend also hides those menus,
but that is decoration — the server is what refuses.

The first admin cannot come from the public register endpoint, so roles are granted
at startup from configuration:

```bash
NEXBID_ADMIN_EMAILS=you@example.com ./mvnw spring-boot:run
```

---

## Response shape

Every endpoint answers in one of two shapes (spec §28), so the client needs one
parser instead of one per route.

```json
{ "success": true, "message": "Bid placed", "data": { "currentPrice": 18500000 } }
```

```json
{ "success": false, "code": "BID_TOO_LOW", "message": "...", "timestamp": "..." }
```

`code` is the contract, `message` is for developers. The client translates the
code, so a Vietnamese bidder reads a Vietnamese reason for the same rejection.

Both ends declare the same list:
[`ErrorCode.java`](backend/src/main/java/com/nexbid/common/exception/ErrorCode.java)
and the `ErrorCode` union in [`types/index.ts`](frontend/src/types/index.ts).
They have to change together.

---

## Test

```bash
cd backend && ./mvnw test
```

```bash
cd frontend && npm run build
```

The backend suite needs the database running.

---

## Docs

The specification and the step-by-step implementation guide are in
[`docs/`](docs/).
