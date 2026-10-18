import http from 'k6/http';
import { check } from 'k6';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';

const GATEWAY = __ENV.GATEWAY_URL || 'http://localhost:9191';
const INCIDENT = __ENV.INCIDENT_URL || 'http://localhost:8082';
const SUMMARY_DIR = __ENV.SUMMARY_DIR || '.';

// JVM warm-up (JIT, connection pools) - not evaluated by the thresholds
const WARMUP = '20s';

const warmup = (exec) => ({
  executor: 'constant-arrival-rate',
  rate: 5,
  timeUnit: '1s',
  duration: WARMUP,
  preAllocatedVUs: 5,
  maxVUs: 20,
  exec,
});

// Ramp-up, plateau and ramp-down; fixed requests/s regardless of response time
const load = (exec, target) => ({
  executor: 'ramping-arrival-rate',
  startTime: WARMUP,
  startRate: 0,
  timeUnit: '1s',
  preAllocatedVUs: 20,
  maxVUs: 100,
  stages: [
    { duration: '15s', target },
    { duration: '45s', target },
    { duration: '10s', target: 0 },
  ],
  exec,
});

export const options = {
  scenarios: {
    warmup_incidents: warmup('incidentsRead'),
    warmup_gateway: warmup('gatewayRouting'),
    // Reading incidents from the database (internal API; behind the gateway it needs a Google OAuth JWT)
    incidents_read: load('incidentsRead', 20),
    // Full path through the gateway: routing -> Eureka (lb://) -> incident-service
    gateway_routing: load('gatewayRouting', 15),
    // The gateway auth filter must reject requests without a token quickly
    gateway_auth_reject: load('gatewayAuthReject', 15),
  },
  // SLO: p95 is the main criterion; p99 leaves headroom for single GC pauses
  // on a shared runner so the pipeline does not become flaky
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{scenario:incidents_read}': ['p(95)<1000', 'p(99)<3000'],
    'http_req_duration{scenario:gateway_routing}': ['p(95)<1000', 'p(99)<3000'],
    'http_req_duration{scenario:gateway_auth_reject}': ['p(95)<500', 'p(99)<2000'],
    // The arrival-rate executor drops iterations when the system cannot keep up (~1% of ~3000)
    dropped_iterations: ['count<30'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function incidentsRead() {
  const res = http.get(`${INCIDENT}/incidents/all`, { tags: { name: 'GET /incidents/all' } });
  check(res, {
    'incidents: status 200': (r) => r.status === 200,
    'incidents: niepusta lista': (r) => Array.isArray(r.json()) && r.json().length > 0,
  });
}

export function gatewayRouting() {
  const res = http.get(`${GATEWAY}/incident-service/v3/api-docs`, {
    tags: { name: 'GET gateway /incident-service/v3/api-docs' },
  });
  check(res, {
    'gateway routing: status 200': (r) => r.status === 200,
    'gateway routing: odpowiedź z incident-service': (r) => r.body.includes('"openapi"'),
  });
}

export function gatewayAuthReject() {
  const res = http.get(`${GATEWAY}/incidents/all`, {
    tags: { name: 'GET gateway /incidents/all (bez tokenu)' },
    // 401 is the expected result here, not an error
    responseCallback: http.expectedStatuses(401),
  });
  check(res, { 'gateway auth: status 401': (r) => r.status === 401 });
}

function markdownSummary(data) {
  const ms = (v) => (v === undefined ? '-' : `${v.toFixed(0)} ms`);
  const rows = ['incidents_read', 'gateway_routing', 'gateway_auth_reject'].map((s) => {
    const m = data.metrics[`http_req_duration{scenario:${s}}`];
    const ok = m && Object.values(m.thresholds || {}).every((t) => t.ok);
    return `| ${ok ? '✅' : '❌'} | ${s} | ${ms(m && m.values.med)} | ${ms(m && m.values['p(95)'])} | ${ms(m && m.values['p(99)'])} |`;
  });
  const reqs = data.metrics.http_reqs.values;
  const failed = data.metrics.http_req_failed.values.rate * 100;
  return [
    '### Load test (k6)',
    '',
    `Żądania: **${reqs.count}** (${reqs.rate.toFixed(1)}/s), błędy: **${failed.toFixed(2)}%**`,
    '',
    '| | Scenariusz | median | p95 | p99 |',
    '|---|---|---|---|---|',
    ...rows,
    '',
  ].join('\n');
}

export function handleSummary(data) {
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: false }),
    [`${SUMMARY_DIR}/summary.json`]: JSON.stringify(data, null, 2),
    [`${SUMMARY_DIR}/summary.md`]: markdownSummary(data),
  };
}
