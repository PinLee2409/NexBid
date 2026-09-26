// EN: k6 load test for bidding (guide §41, spec §33). One run per level:
//
//       docker compose --profile load-test run --rm -e VUS=100 k6
//
//     Each virtual user is one signed-in buyer who opens a lot page, reads it for a moment, bids the
//     minimum next amount, and now and then browses the catalogue. That pace keeps every buyer under the
//     rate limit (10 bids / 10 s), so what is measured is the bid path, not the limiter saying no.
//     Ten lots are shared by all buyers: at 1000 users, a hundred people fight over each lot.
// VI: Load test k6 cho việc trả giá (guide §41, spec §33). Mỗi mức tải chạy một lần. Mỗi người dùng ảo
//     là một người mua đã đăng nhập: mở trang lô, đọc một lát, trả mức tối thiểu kế tiếp, thỉnh thoảng
//     lướt danh mục. Nhịp đó giữ mọi người dưới giới hạn tần suất (10 lượt / 10 giây), nên thứ được đo là
//     đường trả giá chứ không phải bộ giới hạn từ chối. Mười lô dùng chung cho mọi người: ở 1000 người,
//     mỗi lô có một trăm người tranh nhau.

import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 10);
const RAMP = Number(__ENV.RAMP_SECONDS || 15);
const HOLD = Number(__ENV.HOLD_SECONDS || 60);
const LOTS = 10;

const bidDuration = new Trend('bid_duration', true);
const detailDuration = new Trend('detail_duration', true);
const listDuration = new Trend('list_duration', true);
const bidsAccepted = new Counter('bids_accepted');
const bidsOutbid = new Counter('bids_outbid');
const requests = new Counter('requests');
const errors = new Rate('request_errors');

export const options = {
  scenarios: {
    buyers: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${RAMP}s`, target: VUS },
        { duration: `${HOLD}s`, target: VUS },
      ],
      gracefulRampDown: '5s',
    },
  },
  setupTimeout: '5m',
  summaryTrendStats: ['count', 'avg', 'med', 'p(95)', 'p(99)', 'max'],
  // EN: Spec §31 targets. A breach fails the run but the results are still written.
  // VI: Mục tiêu của spec §31. Vượt ngưỡng thì lần chạy bị đánh trượt nhưng kết quả vẫn được ghi.
  thresholds: {
    bid_duration: ['p(95)<500'],
    detail_duration: ['p(95)<300'],
    list_duration: ['p(95)<500'],
    request_errors: ['rate<0.01'],
  },
};

// EN: Lot n was seeded as 10ad0000-0000-4000-8000-<n padded to 12> (see seed.sql).
// VI: Lô n được seed với id 10ad0000-0000-4000-8000-<n đệm đủ 12 số> (xem seed.sql).
const lotId = (n) => `10ad0000-0000-4000-8000-${String(n).padStart(12, '0')}`;

const json = { 'Content-Type': 'application/json' };

/**
 * EN: One account per virtual user, reused across runs; register answers 409 the second time.
 * VI: Mỗi người dùng ảo một tài khoản, dùng lại qua các lần chạy; lần thứ hai đăng ký trả về 409.
 */
export function setup() {
  const tokens = [];
  for (let from = 0; from < VUS; from += 50) {
    const batch = [];
    for (let i = from; i < Math.min(from + 50, VUS); i++) {
      batch.push({ email: `loadtest.${i}@nexbid.local`, password: 'loadtest-password', fullName: `Load Tester ${i}` });
    }
    http.batch(batch.map((user) => ['POST', `${BASE}/api/auth/register`, JSON.stringify(user), { headers: json }]));
    const logins = http.batch(batch.map((user) => ['POST', `${BASE}/api/auth/login`,
      JSON.stringify({ email: user.email, password: user.password }), { headers: json }]));
    logins.forEach((login, i) => {
      if (login.status !== 200) {
        throw new Error(`login ${batch[i].email} answered ${login.status}: ${login.body}`);
      }
      tokens.push(login.json('data.accessToken'));
    });
  }

  const lots = [];
  for (let n = 1; n <= LOTS; n++) {
    const lot = http.get(`${BASE}/api/auctions/${lotId(n)}`);
    if (lot.status !== 200 || lot.json('data.auction.status') !== 'ACTIVE') {
      throw new Error(`lot ${lotId(n)} is not open (${lot.status}); run docs/load-test/seed.sql first`);
    }
    lots.push(lotId(n));
  }
  return { tokens, lots };
}

function track(response, trend, expected) {
  trend.add(response.timings.duration);
  requests.add(1);
  errors.add(!expected.includes(response.status));
}

export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const lot = data.lots[(__VU - 1) % data.lots.length];

  const detail = http.get(`${BASE}/api/auctions/${lot}`, { tags: { name: 'detail' } });
  track(detail, detailDuration, [200]);
  if (detail.status !== 200) {
    sleep(1);
    return;
  }

  // EN: Reading the page. / VI: Đọc trang.
  sleep(0.5 + Math.random() * 0.5);

  const next = detail.json('data.minimumNextBid');
  const step = detail.json('data.auction.minimumIncrement');
  const amount = next + step * Math.floor(Math.random() * 3);
  const bid = http.post(`${BASE}/api/auctions/${lot}/bids`, JSON.stringify({ amount }), {
    headers: { ...json, Authorization: `Bearer ${token}` },
    tags: { name: 'bid' },
  });
  // EN: 422 is the rule answering: someone else raised the price between the page view and the bid.
  // VI: 422 là luật trả lời: người khác đã nâng giá trong lúc từ khi xem trang tới khi trả giá.
  track(bid, bidDuration, [201, 422]);
  if (bid.status === 201) {
    bidsAccepted.add(1);
  } else if (bid.status === 422) {
    bidsOutbid.add(1);
  }

  if (__ITER % 10 === 0) {
    const list = http.get(`${BASE}/api/auctions?status=ACTIVE&size=12`, { tags: { name: 'list' } });
    track(list, listDuration, [200]);
  }

  sleep(0.5 + Math.random() * 0.5);
}

const pick = (metric, stat) => (metric && metric.values[stat] !== undefined ? metric.values[stat] : null);
const ms = (value) => (value === null ? null : Math.round(value * 10) / 10);

export function handleSummary(data) {
  const m = data.metrics;
  const seconds = RAMP + HOLD;
  const trend = (metric) => ({
    count: pick(metric, 'count'),
    p50_ms: ms(pick(metric, 'med')),
    p95_ms: ms(pick(metric, 'p(95)')),
    p99_ms: ms(pick(metric, 'p(99)')),
    max_ms: ms(pick(metric, 'max')),
  });
  const bids = pick(m.bid_duration, 'count') || 0;
  const result = {
    vus: VUS,
    seconds,
    rps: Math.round(((pick(m.requests, 'count') || 0) / seconds) * 10) / 10,
    bid_rps: Math.round((bids / seconds) * 10) / 10,
    error_rate: pick(m.request_errors, 'rate'),
    bids_accepted: pick(m.bids_accepted, 'count') || 0,
    bids_outbid: pick(m.bids_outbid, 'count') || 0,
    bid: trend(m.bid_duration),
    detail: trend(m.detail_duration),
    list: trend(m.list_duration),
    thresholds_passed: Object.values(m).every((metric) =>
      !metric.thresholds || Object.values(metric.thresholds).every((t) => t.ok)),
  };

  const line = (name, t) => `  ${name.padEnd(7)} n=${t.count}  p50=${t.p50_ms}ms  p95=${t.p95_ms}ms  p99=${t.p99_ms}ms  max=${t.max_ms}ms\n`;
  const text = `\n${VUS} users, ${seconds}s: ${result.rps} req/s (${result.bid_rps} bids/s), `
    + `error rate ${(result.error_rate * 100).toFixed(2)}%, bids accepted ${result.bids_accepted}, outbid ${result.bids_outbid}\n`
    + line('bid', result.bid) + line('detail', result.detail) + line('list', result.list)
    + `  spec §31 targets met: ${result.thresholds_passed}\n`;

  return {
    stdout: text,
    [`${__ENV.RESULTS_DIR || '/scripts/results'}/${VUS}-vus.json`]: JSON.stringify(result, null, 2),
  };
}
