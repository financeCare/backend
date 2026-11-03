import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:18080/api';
const LABEL = __ENV.LABEL || 'run';

export const options = {
  scenarios: {
    mixed: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 50),
      duration: __ENV.DURATION || '60s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    // declared so k6 records per-endpoint sub-metrics
    'http_req_duration{endpoint:monthly_status}': ['max>=0'],
    'http_req_duration{endpoint:strategies}': ['max>=0'],
    'http_req_duration{endpoint:debts}': ['max>=0'],
    'http_req_duration{endpoint:overview_graph}': ['max>=0'],
    'http_req_duration{endpoint:priorities}': ['max>=0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const res = http.post(`${BASE}/auth/demo-login`);
  check(res, { 'demo login 200': (r) => r.status === 200 });
  return { token: res.json('accessToken') };
}

// Read-heavy mix modelled on the app home screen. The first two endpoints are Redis-cached.
const READS = [
  ['monthly_status', '/debts/monthly-status'],
  ['strategies', '/repayment-plans/strategies'],
  ['debts', '/debts'],
  ['overview_graph', '/debts/overview-graph'],
  ['priorities', '/debts/priorities'],
];

// ONLY=monthly_status,strategies limits the mix to those endpoints (isolates the cached ones)
const ONLY = (__ENV.ONLY || '').split(',').filter(Boolean);
const ACTIVE = READS.filter(([name]) => ONLY.length === 0 || ONLY.includes(name));

export default function (data) {
  const params = { headers: { Authorization: `Bearer ${data.token}` } };
  for (const [name, path] of ACTIVE) {
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
    per_endpoint: {},
  };
  for (const [name] of READS) {
    const k = `http_req_duration{endpoint:${name}}`;
    if (m[k]) out.per_endpoint[name] = { avg: m[k].values.avg, p95: m[k].values['p(95)'] };
  }
  return { [`results/${LABEL}.json`]: JSON.stringify(out, null, 2), stdout: JSON.stringify(out, null, 2) + '\n' };
}
