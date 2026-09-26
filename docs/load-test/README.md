# Load test

k6 against the Docker stack from `docker compose up` — one backend container, as shipped.
Guide §41 asks for 10 / 100 / 500 / 1000 users with the focus on `POST /api/auctions/{id}/bids`.

![Bid latency under load](bid-latency.svg)

## Results

Each level ramps up for 15 s and holds for 60 s. Latencies in milliseconds, p50 / p95 / p99.

| Users | Requests/s | Bids/s | Errors | `POST …/bids` | `GET /auctions/{id}` | `GET /auctions` | DB connections busy | Redis hit rate* |
| ---: | ---: | ---: | ---: | --- | --- | --- | ---: | ---: |
| 10 | 12.6 | 6.0 | 0.00% | 15.2 / 26.0 / 36.0 | 4.4 / 8.3 / 11.6 | 7.9 / 15.4 / 39.4 | 1 of 10 | 97.9% |
| 100 | 127.1 | 60.3 | 0.00% | 3.5 / 13.3 / 18.0 | 2.4 / 4.2 / 5.3 | 3.9 / 6.7 / 8.3 | 3 of 10 | 99.2% |
| 500 | 637.9 | 302.3 | 0.00% | 2.1 / 9.3 / 13.6 | 1.8 / 3.0 / 4.8 | 2.6 / 4.2 / 6.4 | 3 of 10 | 99.3% |
| 1000 | 1270.6 | 602.2 | 0.00% | 2.1 / 9.3 / 27.1 | 1.9 / 3.9 / 20.3 | 2.4 / 4.5 / 14.5 | 4 of 10 | 99.3% |

Spec §31 targets — bid p95 < 500 ms, lot page p95 < 300 ms, catalogue p95 < 500 ms — hold at every level,
with room to spare. "Errors" counts anything other than the expected answers: 200 for reads, and for a bid
either 201 (accepted) or 422 `BID_TOO_LOW` (someone raised the price between the page view and the bid). No
request failed, timed out or hit the rate limiter.

* Lot cards and rate-limit windows. Each lot page also asks Redis how many people are watching it (spec
§20.3); a lot nobody has open over the socket has no such key, and Redis counts the lookup as a miss — one per
page view in this test, since k6 opens no sockets. Those are left out; with them the raw rate is about 75%.

| Users | Bids accepted | Bids outbid (422) |
| ---: | ---: | ---: |
| 10 | 449 | 0 |
| 100 | 1,377 | 3,142 |
| 500 | 2,250 | 20,423 |
| 1000 | 2,530 | 42,633 |

## What it shows

- **Flat all the way to 1000 users.** Bid p95 stays between 9 and 26 ms and p99 under 40 ms while traffic
  grows a hundred-fold. The 10-user row is the slowest because it runs first, on a JVM that has just started.
- **The knee the first run found is gone.** The first measurement had bid p95 climbing to 200 ms (p99
  413 ms) at 1000 users, with 8 of the 10 pooled database connections busy at once: every authenticated
  request looked the account up to check it was not blocked. `TokenAuthenticator` now reuses that answer
  for 10 seconds and drops it the moment an admin blocks or unblocks the account. At 1000 users bid p95 is
  now 9.3 ms, the lot page 3.9 ms, and at most 4 connections were busy — the pool size did not need to grow.
- **Contention is real.** At 1000 users a hundred people bid on each lot, all offering the same minimum
  next amount, so most lose the race — correctly, with a 422, never a 500. The pessimistic lock serialises
  them; `ConcurrentBidHttpTest` proves the outcome is still exactly one winner per price.
- **Redis carries the lot pages.** 99.3% of lot-card and rate-limit lookups hit — the parts of a lot that
  bidding never changes are served without touching the database.

## Method

Each virtual user is one signed-in buyer on one of ten lots. In a loop it opens the lot page, reads it for
0.5–1 s, bids the minimum next amount (plus 0–2 steps), waits 0.5–1 s, and every tenth round also loads
the catalogue. That keeps each buyer under the rate limit of 10 bids per 10 s, so the numbers measure the
bid path rather than the limiter refusing. DB connections are sampled once a second from
`pg_stat_activity`; the Redis hit rate is the change in `keyspace_hits` / `keyspace_misses` during a run.

Machine: Intel Core i5-12500H (12 cores / 16 threads), 24 GB RAM, Windows 11, Docker Desktop 29.5 with
16 CPUs and 11.5 GB for the VM. k6 ran in a container on the same machine and the same compose network,
so it competed with the backend for CPU. These are one laptop's numbers, not a capacity plan.

## Run it

```bash
docker compose up -d
bash docs/load-test/run.sh
```

`run.sh` seeds ten open lots ([`seed.sql`](seed.sql)), runs [`bid-load.js`](bid-load.js) at each level,
and writes one pair of files per level to [`results/`](results/): k6's measurements (`<n>-vus.json`) and
the database and Redis samples (`<n>-vus-infra.json`). `LEVELS="10 100"` runs a subset. If the stack was started with `NEXBID_BACKEND_PORT` / `NEXBID_FRONTEND_PORT` (or
`COMPOSE_PROJECT_NAME`), export the same values for `run.sh`: its `docker compose run` re-reads them, and with the
defaults it tries to recreate the backend on port 8080. A single level
without the sampling:

```bash
docker compose --profile load-test run --rm -e VUS=100 k6
```
