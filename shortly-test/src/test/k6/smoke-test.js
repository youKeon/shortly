import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

/**
 * Smoke test: lightweight verification of shorten -> redirect -> stats.
 * Duration: 30 seconds.
 */
const TEST_DURATION_SEC = 30;

const smokeSuccessRate = new Rate('smoke_success_rate');
const shortenDuration = new Trend('smoke_shorten_duration');
const redirectDuration = new Trend('smoke_redirect_duration');
const statsDuration = new Trend('smoke_stats_duration');

export const options = {
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: 6,
      duration: '30s',
      exec: 'smokeFlow',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    smoke_success_rate: ['rate>0.95'],
    http_req_duration: ['p(95)<800'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const URL_SERVICE = __ENV.URL_SERVICE || 'http://localhost:8081';
const REDIRECT_SERVICE = __ENV.REDIRECT_SERVICE || 'http://localhost:8082';
const CLICK_SERVICE = __ENV.CLICK_SERVICE || 'http://localhost:8083';

const POPULAR_SITES = [
  'https://github.com',
  'https://stackoverflow.com',
  'https://news.ycombinator.com',
  'https://producthunt.com',
  'https://medium.com',
];

let shortCodes = [];

function pickUrl() {
  return POPULAR_SITES[Math.floor(Math.random() * POPULAR_SITES.length)];
}

function seedShortCodes() {
  const codes = [];
  const seedCount = 6;
  for (let i = 0; i < seedCount; i++) {
    const payload = JSON.stringify({ originalUrl: `${pickUrl()}/smoke/${i}-${Date.now()}` });
    const params = { headers: { 'Content-Type': 'application/json' } };
    const res = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload, params);

    if (res.status === 200 || res.status === 201) {
      try {
        const body = JSON.parse(res.body);
        if (body.shortCode) {
          codes.push(body.shortCode);
        }
      } catch (e) {}
    }
  }
  return codes;
}

export function setup() {
  console.log('\n===== Smoke Test Setup =====');

  const services = [
    { name: 'URL Service', url: `${URL_SERVICE}/actuator/health` },
    { name: 'Redirect Service', url: `${REDIRECT_SERVICE}/actuator/health` },
    { name: 'Click Service', url: `${CLICK_SERVICE}/actuator/health` },
  ];

  services.forEach((service) => {
    const res = http.get(service.url);
    if (res.status !== 200) {
      throw new Error(`${service.name} is not ready (status: ${res.status})`);
    }
    console.log(`✓ ${service.name} OK`);
  });

  const seededCodes = seedShortCodes();
  if (seededCodes.length === 0) {
    throw new Error('Failed to seed short codes for smoke test');
  }

  console.log(`Seed prepared: ${seededCodes.length} codes`);
  console.log('===== Setup Complete. Smoke test running for 30 seconds =====\n');

  return { shortCodes: seededCodes, startedAt: Date.now() };
}

export function smokeFlow(data) {
  if (shortCodes.length === 0 && data?.shortCodes) {
    shortCodes = data.shortCodes;
  }

  const action = Math.random();

  // 35%: create short link
  if (action < 0.35) {
    const payload = JSON.stringify({
      originalUrl: `${pickUrl()}/smoke/${__VU}-${__ITER}-${Date.now()}`,
    });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const start = Date.now();
    const res = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload, params);
    const duration = Date.now() - start;

    const success = check(res, {
      'shorten status 200/201': (r) => r.status === 200 || r.status === 201,
    });

    smokeSuccessRate.add(success);
    shortenDuration.add(duration);

    if (success) {
      try {
        const code = JSON.parse(res.body).shortCode;
        if (code) shortCodes.push(code);
      } catch (e) {}
    }
  }

  // 45%: redirect
  else if (action < 0.80 && shortCodes.length > 0) {
    const shortCode = shortCodes[Math.floor(Math.random() * shortCodes.length)];
    const start = Date.now();
    const res = http.get(`${REDIRECT_SERVICE}/r/${shortCode}`, {
      redirects: 0,
      timeout: '5s'
    });
    const duration = Date.now() - start;

    const success = check(res, {
      'redirect status 302': (r) => r.status === 302,
      'redirect has location': (r) => r.headers['Location'] !== undefined,
    });

    smokeSuccessRate.add(success);
    redirectDuration.add(duration);
  }

  // 20%: stats lookup (or fallback when no codes)
  else {
    const targetCode =
      shortCodes.length > 0
        ? shortCodes[Math.floor(Math.random() * shortCodes.length)]
        : null;

    const start = Date.now();
    const res = targetCode
      ? http.get(`${CLICK_SERVICE}/api/v1/analytics/${targetCode}/stats`)
      : http.get(`${CLICK_SERVICE}/actuator/health`);
    const duration = Date.now() - start;

    const success = check(res, {
      'stats status 200': (r) => r.status === 200,
    });

    smokeSuccessRate.add(success);
    statsDuration.add(duration);
  }

  sleep(0.2);
}

export function teardown(data) {
  if (!data?.startedAt) return;
  const elapsed = Math.round((Date.now() - data.startedAt) / 1000);
  console.log(`\n===== Smoke Test Completed (${elapsed}s) =====\n`);
}

export function handleSummary(data) {
  const totalReqs = data.metrics.http_reqs?.values?.count || 0;
  const totalFailures = data.metrics.http_req_failed?.values?.count || 0;
  const successRate = data.metrics.smoke_success_rate?.values?.rate || 0;

  console.log('\n===== Smoke Test Summary =====');
  console.log(`Duration: ${TEST_DURATION_SEC}s`);
  console.log(`Total Requests: ${totalReqs}`);
  console.log(`Failures: ${totalFailures}`);
  console.log(`Success Rate: ${(successRate * 100).toFixed(2)}%`);
  console.log('==============================\n');

  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'smoke-result.json': JSON.stringify(
      {
        durationSeconds: TEST_DURATION_SEC,
        totalRequests: totalReqs,
        failures: totalFailures,
        successRate: successRate,
        generatedAt: new Date().toISOString(),
      },
      null,
      2,
    ),
  };
}
