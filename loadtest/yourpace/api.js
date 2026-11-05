import http from 'k6/http';
import { check, sleep } from 'k6';

// Same shape as loadtest/k6/api.js (FinanceCare): one login, then a read-heavy mix of 5 authenticated endpoints.
const BASE = __ENV.BASE || 'http://localhost:18099/api';
const LABEL = __ENV.LABEL || 'yourpace';
const EMAIL = __ENV.EMAIL || 'loadtest@example.com';
const PASSWORD = __ENV.PASSWORD || 'loadtest-password';

export const options = {
  scenarios: {
    mixed: { executor: 'constant-vus', vus: Number(__ENV.VUS || 50), duration: __ENV.DURATION || '60s' },
  },
  thresholds: { http_req_failed: ['rate<0.01'] },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const READS = [
  ['health_profile', '/users/health-profile'],
  ['health_summary', '/users/health-summary'],
  ['water_today', '/water/today'],
  ['dashboard_summary', '/dashboard/summary'],
  ['weight_history', '/health/weight/history'],
];

export function setup() {
  const res = http.post(`${BASE}/auth/login`, JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } });
  check(res, { 'login 200': (r) => r.status === 200 });
  return { token: res.json('accessToken') };
}

export default function (data) {
  const params = { headers: { Authorization: `Bearer ${data.token}` } };
  for (const [name, path] of READS) {
    const res = http.get(`${BASE}${path}`, { ...params, tags: { endpoint: name } });
    check(res, { [`${name} 200`]: (r) => r.status === 200 });
  }
  sleep(0.1);
}

export function handleSummary(data) {
  const m = data.metrics;
  const out = {
    label: LABEL,
    rps: m.http_reqs.values.rate,
    avg_ms: m.http_req_duration.values.avg,
    p95_ms: m.http_req_duration.values['p(95)'],
    p99_ms: m.http_req_duration.values['p(99)'],
    error_rate: m.http_req_failed.values.rate,
  };
  return { [`results/${LABEL}.json`]: JSON.stringify(out, null, 2), stdout: JSON.stringify(out, null, 2) + '\n' };
}
