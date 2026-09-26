# Load test

k6 against the Docker stack from `docker compose up` — one backend container, as shipped.
Guide §41 asks for 10 / 100 / 500 / 1000 users with the focus on `POST /api/auctions/{id}/bids`.

![Bid latency under load](bid-latency.svg)

## Results

Each level ramps up for 15 s and holds for 60 s. Latencies in milliseconds, p50 / p95 / p99.

| Users | Requests/s | Bids/s | Errors | `POST …/bids` | `GET /auctions/{id}` | `GET /auctions` | DB connections busy | Redis hit rate |
| ---: | ---: | ---: | ---: | --- | --- | --- | ---: | ---: |
| 10 | 12.6 | 6.0 | 0.00% | 10.9 / 14.3 / 16.9 | 2.8 / 3.9 / 4.8 | 4.4 / 5.5 / 6.9 | 2 of 10 | 99.3% |
| 100 | 126.9 | 60.1 | 0.00% | 3.7 / 12.2 / 16.9 | 1.9 / 3.1 / 4.0 | 3.3 / 5.2 / 7.2 | 3 of 10 | 99.3% |
| 500 | 637.0 | 301.9 | 0.00% | 3.0 / 11.5 / 19.4 | 1.7 / 3.5 / 9.4 | 2.9 / 6.1 / 12.5 | 3 of 10 | 99.3% |
| 1000 | 1242.1 | 588.6 | 0.00% | 3.1 / 200.3 / 413.3 | 1.8 / 114.3 / 250.6 | 2.7 / 110.1 / 243.7 | 8 of 10 | 99.3% |

Spec §31 targets — bid p95 < 500 ms, lot page p95 < 300 ms, catalogue p95 < 500 ms — hold at every level.
"Errors" counts anything other than the expected answers: 200 for reads, and for a bid either 201
(accepted) or 422 `BID_TOO_LOW` (someone raised the price between the page view and the bid). No request
failed, timed out or hit the rate limiter.

| Users | Bids accepted | Bids outbid (422) |
| ---: | ---: | ---: |
| 10 | 448 | 0 |
| 100 | 1,391 | 3,118 |
| 500 | 2,277 | 20,364 |
| 1000 | 2,527 | 41,616 |

## What it shows

- **Flat up to 500 users.** Bid p99 stays under 20 ms while traffic grows fifty-fold. The 10-user row is
  the slowest at p50 because it runs first, on a JVM that has just started.
- **The knee is between 500 and 1000 users.** p50 does not move, but p95 climbs to 200 ms and the busiest
  moment has 8 of the 10 pooled database connections working at once. Every request queues for the same
  ten connections — and each authenticated request spends one extra lookup checking that the account is
  not blocked. A larger pool, or caching that check, are the first things to try; neither has been
  measured yet.
- **Contention is real.** At 1000 users a hundred people bid on each lot, all offering the same minimum
  next amount, so most lose the race — correctly, with a 422, never a 500. The pessimistic lock serialises
  them; `ConcurrentBidHttpTest` proves the outcome is still exactly one winner per price.
- **Redis carries the lot pages.** 99.3% of key lookups hit — the lot cards (the parts of a lot that
  bidding never changes) and the per-user rate-limit windows.

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
the database and Redis samples (`<n>-vus-infra.json`). `LEVELS="10 100"` runs a subset. A single level
without the sampling:

```bash
docker compose --profile load-test run --rm -e VUS=100 k6
```
