// Cadence gateway load test — k6
//
// Run via Docker (no k6 install needed):
//   docker run --rm -v "${PWD}/scripts:/scripts" grafana/k6 run /scripts/load-test.js
//
// What it does:
// - Each VU: logs in once, then loops over a realistic browse mix
//   (discover feed + artist listing + global search + a playlist read).
// - Stages ramp 0→25 VUs over 30s, hold 25 for 60s, ramp down — exercises the
//   gateway under sustained load without crushing a laptop.
// - Asserts every response is HTTP 2xx; failures count toward `checks` rate
//   and the threshold below fails the test if any path 5xxes.

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const EMAIL = __ENV.EMAIL || 'Adella56@yahoo.com';
const PASSWORD = __ENV.PASSWORD || 'password';

const errors = new Rate('cadence_errors');
const loginLatency = new Trend('cadence_login_ms');
const discoverLatency = new Trend('cadence_discover_ms');
const searchLatency = new Trend('cadence_search_ms');

export const options = {
  stages: [
    { duration: '30s', target: 25 },  // ramp to 25 concurrent users
    { duration: '60s', target: 25 },  // hold for 1 min
    { duration: '15s', target: 0 },   // ramp down
  ],
  thresholds: {
    // Fewer than 1% of any check should fail
    cadence_errors: ['rate<0.01'],
    // p95 latency budgets (generous — local Docker, not prod hardware)
    'http_req_duration{name:login}': ['p(95)<2000'],
    'http_req_duration{name:discover}': ['p(95)<3000'],
    'http_req_duration{name:search}': ['p(95)<2000'],
  },
};

// Each VU logs in once at the start of the iteration sequence.
// k6 doesn't have per-VU setup so we cache the {token,userId} in a closure-ish var.
let cachedAuth = null;

function login() {
  if (cachedAuth) return cachedAuth;

  const res = http.post(
    `${BASE}/auth/v1/authenticate`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'login' } }
  );

  loginLatency.add(res.timings.duration);
  const ok = check(res, {
    'login: 201': (r) => r.status === 201,
    'login: has token': (r) => r.json('data.accessToken') != null,
  });
  if (!ok) {
    errors.add(1);
    return null;
  }

  cachedAuth = {
    token: res.json('data.accessToken'),
    userId: res.json('data.id'),
  };
  return cachedAuth;
}

export default function () {
  const auth = login();
  if (!auth) {
    sleep(1);
    return;
  }
  const authHeaders = {
    headers: { Authorization: `Bearer ${auth.token}`, 'Content-Type': 'application/json' },
  };

  // ── Discover feed (heaviest path: catalog → playlist + streaming via Feign) ──
  group('discover', () => {
    const res = http.get(`${BASE}/api/v1/discover`, {
      ...authHeaders,
      tags: { name: 'discover' },
    });
    discoverLatency.add(res.timings.duration);
    const ok = check(res, { 'discover: 200': (r) => r.status === 200 });
    if (!ok) errors.add(1);
  });

  sleep(0.5);

  // ── Artist listing (catalog only) ──
  group('artists list', () => {
    const res = http.get(`${BASE}/api/v1/artist/all?page=0&size=24`, {
      ...authHeaders,
      tags: { name: 'artists_list' },
    });
    check(res, { 'artists: 200': (r) => r.status === 200 }) || errors.add(1);
  });

  sleep(0.5);

  // ── Global search (catalog → playlist Feign call) ──
  group('search', () => {
    const res = http.get(`${BASE}/api/v1/search?key=a&page=0&size=10`, {
      ...authHeaders,
      tags: { name: 'search' },
    });
    searchLatency.add(res.timings.duration);
    check(res, { 'search: 200': (r) => r.status === 200 }) || errors.add(1);
  });

  sleep(0.5);

  // ── User profile (auth-service → playlist-service Feign call) ──
  group('profile', () => {
    const res = http.get(`${BASE}/api/v1/user/${auth.userId}`, {
      ...authHeaders,
      tags: { name: 'profile' },
    });
    check(res, { 'profile: 200': (r) => r.status === 200 }) || errors.add(1);
  });

  sleep(1);
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data),
    '/scripts/load-test-results.json': JSON.stringify(data, null, 2),
  };
}

// Inline text summary (k6 ships this but importing from jslib needs network)
function textSummary(data) {
  const m = data.metrics;
  const fmt = (n) => (typeof n === 'number' ? n.toFixed(1) : n);
  const get = (key, sub) => (m[key]?.values?.[sub] != null ? fmt(m[key].values[sub]) : '—');
  return `
─── Cadence load test summary ────────────────────────────────────────
  VUs (max):                  ${get('vus_max', 'value')}
  Iterations:                 ${get('iterations', 'count')}
  Total HTTP requests:        ${get('http_reqs', 'count')}
  Throughput (req/s):         ${get('http_reqs', 'rate')}
  Failed requests:            ${get('http_req_failed', 'rate')} (rate)
  Error rate (custom):        ${get('cadence_errors', 'rate')}

  HTTP req duration:
    avg:                      ${get('http_req_duration', 'avg')} ms
    p50:                      ${get('http_req_duration', 'med')} ms
    p95:                      ${get('http_req_duration', 'p(95)')} ms
    p99:                      ${get('http_req_duration', 'p(99)')} ms

  Per-endpoint p95 latency:
    login:                    ${get('cadence_login_ms', 'p(95)')} ms
    discover:                 ${get('cadence_discover_ms', 'p(95)')} ms
    search:                   ${get('cadence_search_ms', 'p(95)')} ms
──────────────────────────────────────────────────────────────────────
`;
}
