// Stepped load test against /api/v1/discover (the heaviest path: catalog →
// playlist + streaming via Feign + DB query). Five constant-VU scenarios run
// back-to-back so we can compare throughput and percentiles per VU level.
//
// Usage (Docker, no install needed):
//   docker run --rm -v "${PWD}/scripts:/scripts" -e EMAIL=<seeded-email> \
//     grafana/k6 run /scripts/load-test-discover-stepped.js \
//     --summary-trend-stats="avg,med,p(95),p(99),max"

import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const EMAIL = __ENV.EMAIL || 'Adella56@yahoo.com';
const PASSWORD = __ENV.PASSWORD || 'password';

// Per-stage trend metrics — k6 doesn't slice trend metrics by tag in the default
// summary, so we keep one metric per VU step and read them out in handleSummary.
const trends = {
  '050': new Trend('discover_050_ms'),
  '100': new Trend('discover_100_ms'),
  '150': new Trend('discover_150_ms'),
  '200': new Trend('discover_200_ms'),
  '250': new Trend('discover_250_ms'),
};
const reqs = {
  '050': new Rate('discover_050_ok'),
  '100': new Rate('discover_100_ok'),
  '150': new Rate('discover_150_ok'),
  '200': new Rate('discover_200_ok'),
  '250': new Rate('discover_250_ok'),
};

const STAGE_DURATION = '30s';
const STAGE_GAP = 10;  // seconds — let prior VUs drain before next stage spins up
const PER_STAGE = 30 + STAGE_GAP;

export const options = {
  // setup() once, then five constant-VU scenarios in sequence.
  scenarios: {
    vu_050: { executor: 'constant-vus', vus: 50,  duration: STAGE_DURATION,
              startTime: `${0 * PER_STAGE}s`,  exec: 'discover', gracefulStop: '5s', tags: { stage: '050' } },
    vu_100: { executor: 'constant-vus', vus: 100, duration: STAGE_DURATION,
              startTime: `${1 * PER_STAGE}s`,  exec: 'discover', gracefulStop: '5s', tags: { stage: '100' } },
    vu_150: { executor: 'constant-vus', vus: 150, duration: STAGE_DURATION,
              startTime: `${2 * PER_STAGE}s`,  exec: 'discover', gracefulStop: '5s', tags: { stage: '150' } },
    vu_200: { executor: 'constant-vus', vus: 200, duration: STAGE_DURATION,
              startTime: `${3 * PER_STAGE}s`,  exec: 'discover', gracefulStop: '5s', tags: { stage: '200' } },
    vu_250: { executor: 'constant-vus', vus: 250, duration: STAGE_DURATION,
              startTime: `${4 * PER_STAGE}s`,  exec: 'discover', gracefulStop: '5s', tags: { stage: '250' } },
  },
  // No hard thresholds — we want to see how it degrades, not fail-fast.
};

export function setup() {
  const res = http.post(
    `${BASE}/auth/v1/authenticate`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  if (res.status !== 201) {
    throw new Error(`setup login failed: ${res.status} ${res.body}`);
  }
  return { token: res.json('data.accessToken') };
}

export function discover(data) {
  const stage = exec.scenario.name.replace('vu_', '');
  const res = http.get(`${BASE}/api/v1/discover`, {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { name: 'discover', stage },
  });
  trends[stage].add(res.timings.duration);
  reqs[stage].add(res.status === 200);
  check(res, { 'discover: 200': (r) => r.status === 200 });
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data),
    '/scripts/load-test-discover-results.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data) {
  const m = data.metrics;
  const fmt = (n, d = 1) => (typeof n === 'number' ? n.toFixed(d) : '—');
  const stages = ['050', '100', '150', '200', '250'];

  let table = '\n┌──────┬─────────┬───────────┬───────────┬───────────┬───────────┬─────────────┐\n';
  table     += '│  VUs │   reqs  │  req/s    │  p50 (ms) │  p95 (ms) │  p99 (ms) │  success %  │\n';
  table     += '├──────┼─────────┼───────────┼───────────┼───────────┼───────────┼─────────────┤\n';

  for (const stage of stages) {
    const t = m[`discover_${stage}_ms`]?.values || {};
    const r = m[`discover_${stage}_ok`]?.values || {};
    // Rate metric exposes passes + fails — total = passes + fails. Trend has
    // no count, so reconstruct it from the Rate companion.
    const passes = r.passes || 0;
    const fails = r.fails || 0;
    const count = passes + fails;
    const rps = count / 30; // each stage runs 30s
    const success = (r.rate || 0) * 100;
    table += `│  ${stage} │ ${String(count).padStart(7)} │ ${fmt(rps).padStart(9)} │ ${fmt(t.med).padStart(9)} │ ${fmt(t['p(95)']).padStart(9)} │ ${fmt(t['p(99)']).padStart(9)} │ ${fmt(success, 2).padStart(11)} │\n`;
  }
  table += '└──────┴─────────┴───────────┴───────────┴───────────┴───────────┴─────────────┘\n';

  return `
─── Cadence stepped load test — /api/v1/discover ──────────────────────────${table}
Total HTTP requests:        ${fmt(m.http_reqs?.values?.count, 0)}
Total iterations:           ${fmt(m.iterations?.values?.count, 0)}
Failed responses (HTTP):    ${fmt(m.http_req_failed?.values?.rate * 100, 2)}%
`;
}
