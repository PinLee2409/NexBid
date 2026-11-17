// EN: k6 load test for the search box's suggestions (GET /api/auctions/suggest). One run per level:
//
//       docker compose --profile load-test run --rm -e VUS=100 k6 run --quiet /scripts/search-load.js
//
//     Each virtual user is a visitor typing a search: one request per keystroke from the second letter on, a
//     typist's pause between keys, then a moment reading the suggestions before the next search. The words are
//     the demo catalogue's (docs/demo/seed.mjs), with and without Vietnamese marks, plus a few typos. Many people
//     typing the same prefixes is exactly what the 30-second Redis cache is for. Results go to
//     results/search-<n>-vus.json, beside (never over) the bidding results.
// VI: Load test k6 cho gợi ý của ô tìm kiếm (GET /api/auctions/suggest). Mỗi mức tải chạy một lần. Mỗi người dùng ảo
//     là một khách đang gõ tìm kiếm: mỗi phím một request từ chữ thứ hai, nghỉ giữa các phím như người gõ thật, rồi
//     đọc gợi ý một lát trước lần tìm tiếp theo. Các từ lấy từ danh mục demo (docs/demo/seed.mjs), có dấu và không
//     dấu, cùng vài lỗi chính tả. Nhiều người gõ cùng các tiền tố chính là việc cache Redis 30 giây được làm ra để lo.
//     Kết quả ghi vào results/search-<n>-vus.json, cạnh (không bao giờ đè lên) kết quả trả giá.

import http from 'k6/http';
import { sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 10);
const RAMP = Number(__ENV.RAMP_SECONDS || 15);
const HOLD = Number(__ENV.HOLD_SECONDS || 60);

const suggestDuration = new Trend('suggest_duration', true);
const requests = new Counter('requests');
const errors = new Rate('request_errors');

const WORDS = [
  'omega', 'seamaster', 'đồng hồ', 'dong ho', 'leica', 'máy ảnh', 'may anh', 'macbook', 'polaroid', 'fujifilm',
  'tranh', 'màu nước', 'mau nuoc', 'sneaker', 'jordan', 'vintage', 'rolex', 'ấm trà', 'am tra', 'bat trang',
  'omgea', 'leika', 'mackbook', 'camera', 'iphone', 'nikon', 'canon', 'gucci', 'hermes', 'lego',
];

export const options = {
  scenarios: {
    visitors: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: `${RAMP}s`, target: VUS },
        { duration: `${HOLD}s`, target: VUS },
      ],
      gracefulRampDown: '5s',
    },
  },
  summaryTrendStats: ['count', 'avg', 'med', 'p(95)', 'p(99)', 'max'],
  // EN: Typing feels instant under ~100 ms; anything slower than 300 ms lags behind the keys.
  // VI: Gõ phím thấy tức thì khi dưới ~100 ms; chậm hơn 300 ms là gợi ý chạy theo sau phím.
  thresholds: {
    suggest_duration: ['p(95)<300'],
    request_errors: ['rate<0.01'],
  },
};

export default function () {
  const word = WORDS[Math.floor(Math.random() * WORDS.length)];
  for (let length = 2; length <= word.length; length++) {
    const prefix = word.slice(0, length);
    if (prefix.endsWith(' ')) continue;
    const response = http.get(`${BASE}/api/auctions/suggest?q=${encodeURIComponent(prefix)}`, {
      tags: { name: 'suggest' },
    });
    requests.add(1);
    suggestDuration.add(response.timings.duration);
    errors.add(response.status !== 200);
    // EN: The frontend waits for a 180 ms pause; a typist's keys come 120–300 ms apart.
    // VI: Frontend chờ khoảng lặng 180 ms; phím của người gõ cách nhau 120–300 ms.
    sleep(0.12 + Math.random() * 0.18);
  }
  sleep(1 + Math.random() * 2);
}

const pick = (metric, stat) => (metric && metric.values[stat] !== undefined ? metric.values[stat] : null);
const ms = (value) => (value === null ? null : Math.round(value * 10) / 10);

export function handleSummary(data) {
  const m = data.metrics;
  const seconds = RAMP + HOLD;
  const result = {
    vus: VUS,
    seconds,
    rps: Math.round(((pick(m.requests, 'count') || 0) / seconds) * 10) / 10,
    error_rate: pick(m.request_errors, 'rate'),
    suggest: {
      count: pick(m.suggest_duration, 'count'),
      p50_ms: ms(pick(m.suggest_duration, 'med')),
      p95_ms: ms(pick(m.suggest_duration, 'p(95)')),
      p99_ms: ms(pick(m.suggest_duration, 'p(99)')),
      max_ms: ms(pick(m.suggest_duration, 'max')),
    },
    thresholds_passed: Object.values(m).every((metric) =>
      !metric.thresholds || Object.values(metric.thresholds).every((t) => t.ok)),
  };
  const s = result.suggest;
  const text = `\n${VUS} visitors typing, ${seconds}s: ${result.rps} req/s, error rate `
    + `${(result.error_rate * 100).toFixed(2)}%\n  suggest n=${s.count}  p50=${s.p50_ms}ms  p95=${s.p95_ms}ms  `
    + `p99=${s.p99_ms}ms  max=${s.max_ms}ms\n  targets met: ${result.thresholds_passed}\n`;
  return {
    stdout: text,
    [`${__ENV.RESULTS_DIR || '/scripts/results'}/search-${VUS}-vus.json`]: JSON.stringify(result, null, 2),
  };
}
