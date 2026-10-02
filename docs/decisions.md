# Design decisions

The choices that shape NexBid, one per section: the problem, what was chosen, what it costs, and where it is
proven. The specification is in [`NexBid_Project_Specification.md`](NexBid_Project_Specification.md); section
numbers below (§) refer to it.

---

## 1. A modular monolith, not microservices

**Problem.** An auction has a few tightly coupled rules (price, clock, winner, payment) and a handful of
loosely coupled concerns (notifications, analytics, audit). The spec lists microservices as out of scope.

**Decision.** One Spring Boot application split into modules (`auction`, `bid`, `payment`, `order`,
`notification`, `analytics`, …). Spring Modulith checks the boundaries at build time: only a module's
top-level package is visible to others. Loosely coupled work reacts to events rather than being called.

**Cost.** One deployable, so one scaling unit. A module can still be split out later without a rewrite,
because the only way in is its public API and its events.

**Proof.** `ModularityTest` fails the build on a boundary violation.

## 2. The server owns price, clock and winner

**Problem.** A browser's clock and a browser's idea of the current price cannot be trusted (§30).

**Decision.** Every rule that decides money or outcome runs on the server: the minimum next bid, whether a lot
is open, anti-sniping extensions, the winner. The client keeps a copy of the rules
(`frontend/src/lib/auction-rules.ts`) only to explain a refusal before sending a request, and it shows the
countdown against the server's clock (`GET /api/server-time`).

**Cost.** Two copies of a few rules. The server copy is the only one that matters; the client copy is
covered by unit tests so it does not mislead.

## 3. Concurrent bids: a row lock, not a retry loop

**Problem.** Many people bid on the same lot in the same second. Each bid must see the bid before it, or two
of them would both win at the same price (§8).

**Decision.** A bid reads its lot with `SELECT … FOR UPDATE` (`AuctionRepository.findByIdForUpdate`). Bids on
one lot are serialised by the database; bids on different lots do not wait for each other. The bid's time is
stamped after the lock is taken, so the order of timestamps is the order of acceptance. The scheduler that
closes lots takes the same lock, so the last bid and the closing bell cannot both believe they came first.
`@Version` on the lot is a second guard: a write based on a stale read is refused rather than applied.

**Cost.** Bids on a hot lot queue behind each other. The load test measured this at 1000 users on 10 shared
lots: bid p95 9 ms, p99 27 ms.

**Proof.** `ConcurrentBidHttpTest` (a hundred simultaneous bids, exactly one winner per price),
`AuctionAutoEndTest.theClosingBellWaitsForABidThatIsStillCommitting`, [`load-test`](load-test/README.md).

## 4. Anti-sniping on the server

**Problem.** A bid in the last second leaves nobody time to answer (§13).

**Decision.** A bid inside the lot's final window (30 s by default) pushes the end time back (120 s by default),
inside the same locked transaction as the bid. Everyone watching gets `AUCTION_EXTENDED` over the socket.

**Proof.** `AntiSnipingTest`.

## 5. Events leave through an outbox

**Problem.** Notifications, analytics and the audit trail must follow every bid and every close, but a Kafka
outage must not stop bidding, and a bid that rolls back must not announce itself.

**Decision.** Events are written to an `outbox` table in the same transaction as the change they describe. A
relay publishes them to Kafka (`nexbid.auctions`, `nexbid.payments`) after commit, and keeps them in the table
while Kafka is down. Consumers are idempotent: each records the event ids it has handled (`consumed_events`),
so a redelivery changes nothing. An event that can never be handled goes to a dead-letter topic (`*.DLT`)
instead of blocking its partition. Realtime pushes to browsers happen after commit, directly, so the lot page
does not wait on Kafka.

**Cost.** At-least-once delivery, handled by idempotency; a small delay between commit and notification.

**Proof.** `AuctionEventsOnKafkaTest` (bids go through while Kafka is down; their notices arrive once it is
back), `OutboxTest` (an event from a rolled-back transaction never leaves), `NotificationConsumerTest` and
`AnalyticsApiTest` (an event delivered twice counts once; the dead-letter topic).

## 6. Redis is a cache and a guard, never the truth

**Problem.** The catalogue and lot pages are read far more than they change; rate limits need a count shared
by every instance.

**Decision.** Redis holds lot cards, viewer counts and rate-limit windows. Every use fails open: if Redis is
down, pages read from PostgreSQL, and limits let requests through. The health check does not report the app as
down for it.

**Cost.** For a few seconds after an outage starts, limits are not enforced.

**Proof.** `RedisOutageTest` (the site keeps working from the database when Redis dies).

## 7. Realtime over STOMP, with a private channel per user

**Problem.** Everyone on a lot page must see bids as they happen (§18, §20.3); notifications belong to one
person.

**Decision.** STOMP over WebSocket with Spring's simple broker. `/topic/auctions/{id}` is public. The access
token travels with `CONNECT`, and a signed-in user's notifications go to `/user/queue/notifications`. Who is
watching a lot is a Redis set per lot, updated on subscribe, unsubscribe and disconnect.

**Cost.** The simple broker lives in one instance. Several instances would need a broker relay (RabbitMQ or
ActiveMQ); the code already goes through Spring's messaging template, so that is configuration.

## 8. Sessions: a short access token and a rotating refresh token

**Problem.** A long-lived token in the browser is a long-lived key for whoever steals it; a short one signs
people out in the middle of an auction (§7.2).

**Decision.** The access token lasts 15 minutes. A refresh token in an `HttpOnly; Secure; SameSite=Strict`
cookie scoped to `/api/auth` renews it; only its SHA-256 is stored. Each refresh token works once: a renewal
replaces it, and a replaced token coming back revokes its whole family. Blocking an account revokes all its
sessions in the same transaction. The frontend renews a minute early, retries once after a 401, and uses a Web
Lock so two tabs never present the same cookie.

**Proof.** `RefreshTokenApiTest`; `session.spec.ts` (including four tabs renewing at once, which sends
exactly one renewal).

## 9. Defences in the browser and at the door

**Decision.**
- Wrong passwords are counted per email and address (5 in 15 minutes) and per address (20). A correct password
  clears its pair, and nobody can lock out someone else from elsewhere.
- Every page carries a Content Security Policy with a fresh nonce: scripts without it and inline event
  handlers do not run.
- The `prod` profile refuses to start with the JWT secret from this repository, a short one, or a refresh
  cookie allowed over plain HTTP, and stops publishing the API docs.

**Cost.** The client address comes from `X-Forwarded-For`, which Next.js passes on but never sets. A
deployment needs a reverse proxy in front that sets it — HTTPS needs one anyway.

**Proof.** `LoginRateLimitTest`, `ProductionGuardTest`, `security.spec.ts`.

## 10. Times: stored in UTC, shown in the reader's zone

**Decision.** The backend stores and returns instants in UTC. The browser reports its zone in a cookie, the
server renders dates in that zone, and the first visit renders once in UTC and then in local time.

**Proof.** `time-zone.spec.ts`, run in two zones on opposite sides of UTC.

## 11. Testing: real infrastructure, real browsers, and tests that fail when they should

**Decision.**
- Backend tests run against real PostgreSQL, Redis and Kafka (Testcontainers), not mocks.
- Playwright drives the built app in Chromium. Every end-to-end test fails on any console error, so hydration
  mismatches and CSP violations cannot pass unnoticed. axe checks every page against WCAG 2.1 AA in both
  themes, and every page is checked at phone width.
- Unit tests cover the frontend's logic layer, with coverage thresholds in CI.
- A test earns its place by failing when the code it guards is broken: the non-obvious ones were checked by
  breaking that code on purpose (a missing lock, a missing Web Lock, a reverted contrast token, a time zone
  pinned back to UTC).

**Cost.** The backend suite takes a few minutes and needs Docker.

## 12. A second chance reuses the sale, rather than adding a parallel one

**Problem.** When a winner does not pay, the spec suggests offering the lot to the runner-up (§17). A second,
separate kind of sale would need its own payment, order, completion and expiry rules.

**Decision.** The seller may offer an unpaid lot once, to the best other bidder, at that bidder's own highest
bid; the product is held while the offer is open. Accepting closes the lot again in the runner-up's name
(`CANCELLED → ENDED`) and opens an ordinary payment, so everything after that — the 48-hour window, the order,
completion, expiry, reminders — is the path every sale already takes. A lot keeps at most one payment and one
order alive at a time (partial unique indexes), and one offer ever (a unique index).

**Cost.** The lot's price stays the hammer price; what the runner-up pays is on their own payment and order.
An auto-bid ceiling the runner-up never had to reach is not their price: only recorded bids count.

**Proof.** `SecondChanceApiTest` (accepted and paid, declined, lapsed, accepted and then unpaid again, and every
refusal), `second-chance.spec.ts`.

## 13. Becoming a seller: an admin decides, the token catches up

**Problem.** Every account starts as a buyer (§7.1), and roles live inside the 15-minute access token, so a
role granted later is refused until the token is replaced.

**Decision.** A buyer sends one request at a time; an admin approves or rejects it with a reason, in the same
transaction as the role change, the notice and the audit row. The page renews its token as soon as the approval
notice arrives, or whenever the account shows a role its token lacks.

**Proof.** `SellerApplicationApiTest` (including four tabs sending at once), `seller-application.spec.ts`.

## 14. One trace across the outbox

**Problem.** The outbox (decision 5) splits a bid in two: the request commits and answers, and later another
thread sends the event to Kafka. A trace would stop at the commit.

**Decision.** The outbox writer stores the W3C `traceparent` of the span that raised the event; the relay sends
the event inside a span continuing that trace, and Kafka carries it on in a record header to both consumer
groups. SQL statements are spans too. Health checks, metric scrapes, the relay's polling and scheduler ticks
start no traces, so what Tempo holds is the work people do.

**Cost.** Traces leave the app only when `NEXBID_TRACING_EXPORT=true`; every request is sampled, which suits a
demo, not heavy traffic (`NEXBID_TRACING_SAMPLING`).

**Proof.** `BidTraceTest` (one bid: the request, its SQL, the relay, the Kafka send, the consumer and the notice it
writes, all under the trace id the caller sent; checked by breaking the relay's link), and that the relay's polling
leaves no spans.
