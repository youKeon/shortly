import http from 'k6/http';
import { check, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

/**
 * 목표: 10,000 TPS (Redirection 9,000 / Shorten 800 / Stats 200) <br>
 * 시간: 8분
 */
const TARGET_TPS = 10000;
const TEST_DURATION_SEC = 480; // 8분

const TRAFFIC_RATIO = {
  shorten: 0.08,
  redirect: 0.9,
  stats: 0.02,
};

const shortenRate = Math.round(TARGET_TPS * TRAFFIC_RATIO.shorten);
const statsRate = Math.max(1, Math.round(TARGET_TPS * TRAFFIC_RATIO.stats));
const redirectRate = TARGET_TPS - shortenRate - statsRate;

const shortenSuccessRate = new Rate('shorten_success_rate');
const redirectSuccessRate = new Rate('redirect_success_rate');
const statsSuccessRate = new Rate('stats_success_rate');

const shortenDuration = new Trend('shorten_duration');
const redirectDuration = new Trend('redirect_duration');
const statsDuration = new Trend('stats_duration');

const totalRequests = new Counter('total_requests');
const totalErrors = new Counter('total_errors');

export const options = {
  scenarios: {
    url_creation: {
      executor: 'constant-arrival-rate',
      rate: shortenRate,
      timeUnit: '1s',
      duration: '8m',
      preAllocatedVUs: 220,
      maxVUs: 1200,
      exec: 'shortenUrl',
    },
    redirection: {
      executor: 'constant-arrival-rate',
      rate: redirectRate,
      timeUnit: '1s',
      duration: '8m',
      preAllocatedVUs: 2400,
      maxVUs: 12000,
      exec: 'redirect',
      startTime: '10s',
    },
    statistics: {
      executor: 'constant-arrival-rate',
      rate: statsRate,
      timeUnit: '1s',
      duration: '8m',
      preAllocatedVUs: 120,
      maxVUs: 500,
      exec: 'getStats',
      startTime: '20s',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    http_req_failed: ['rate<0.05'],
    shorten_success_rate: ['rate>0.95'],
    redirect_success_rate: ['rate>0.95'],
    stats_success_rate: ['rate>0.92'],
    shorten_duration: ['p(95)<320'],
    redirect_duration: ['p(95)<110'],
    stats_duration: ['p(95)<220'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const URL_SERVICE = __ENV.URL_SERVICE || 'http://localhost:8081';
const REDIRECT_SERVICE = __ENV.REDIRECT_SERVICE || 'http://localhost:8082';
const CLICK_SERVICE = __ENV.CLICK_SERVICE || 'http://localhost:8083';

const POPULAR_SITES = [
  'https://github.com',
  'https://stackoverflow.com',
  'https://reddit.com',
  'https://twitter.com',
  'https://youtube.com',
  'https://linkedin.com',
  'https://medium.com',
  'https://dev.to',
  'https://news.ycombinator.com',
  'https://producthunt.com',
];

let globalShortCodes = [];

function pickUrl() {
  return POPULAR_SITES[Math.floor(Math.random() * POPULAR_SITES.length)];
}

export function setup() {
  console.log('\n===== 10K TPS Validation: Setup Started =====');

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

  const codes = [];
  const seedCount = 400;
  for (let i = 0; i < seedCount; i++) {
    const payload = JSON.stringify({ originalUrl: `${pickUrl()}/seed/${i}-${Date.now()}` });
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

  if (codes.length < 250) {
    throw new Error('Seed generation failed (less than 250 codes)');
  }

  console.log(`Seed prepared: ${codes.length}`);
  console.log('===== Setup Complete. Test Running for 8 minutes =====\n');

  return { shortCodes: codes, startTime: Date.now() };
}

export function shortenUrl(data) {
  if (globalShortCodes.length === 0 && data?.shortCodes) {
    globalShortCodes = data.shortCodes;
  }

  group('URL Shortening', () => {
    const url = `${pickUrl()}/perf/${__VU}-${__ITER}-${Date.now()}`;
    const payload = JSON.stringify({ originalUrl: url });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const start = Date.now();
    const res = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload, params);
    const duration = Date.now() - start;

    const success = check(res, {
      'shorten status is 201 or 200': (r) => r.status === 201 || r.status === 200,
      'shorten has shortCode': (r) => {
        try {
          return JSON.parse(r.body).shortCode !== undefined;
        } catch (e) {
          return false;
        }
      },
    });

    shortenSuccessRate.add(success);
    shortenDuration.add(duration);
    totalRequests.add(1);
    if (!success) totalErrors.add(1);

    if (success) {
      try {
        const code = JSON.parse(res.body).shortCode;
        globalShortCodes.push(code);
      } catch (e) {}
    }
  });
}

export function redirect(data) {
  if (globalShortCodes.length === 0 && data?.shortCodes) {
    globalShortCodes = data.shortCodes;
  }
  if (globalShortCodes.length === 0) {
    totalErrors.add(1);
    return;
  }

  group('Redirect', () => {
    const shortCode = globalShortCodes[Math.floor(Math.random() * globalShortCodes.length)];
    const start = Date.now();
    const res = http.get(`${REDIRECT_SERVICE}/r/${shortCode}`, { redirects: 0 });
    const duration = Date.now() - start;

    const success = check(res, {
      'redirect status 302': (r) => r.status === 302,
      'redirect has location': (r) => r.headers['Location'] !== undefined,
    });

    redirectSuccessRate.add(success);
    redirectDuration.add(duration);
    totalRequests.add(1);
    if (!success) totalErrors.add(1);
  });
}

export function getStats(data) {
  if (globalShortCodes.length === 0 && data?.shortCodes) {
    globalShortCodes = data.shortCodes;
  }
  if (globalShortCodes.length === 0) {
    totalErrors.add(1);
    return;
  }

  group('Statistics', () => {
    const shortCode = globalShortCodes[Math.floor(Math.random() * globalShortCodes.length)];
    const start = Date.now();
    const res = http.get(`${CLICK_SERVICE}/api/v1/analytics/${shortCode}/stats`);
    const duration = Date.now() - start;

    const success = check(res, {
      'stats status 200': (r) => r.status === 200,
      'stats has totalClicks': (r) => {
        try {
          return JSON.parse(r.body).totalClicks !== undefined;
        } catch (e) {
          return false;
        }
      },
    });

    statsSuccessRate.add(success);
    statsDuration.add(duration);
    totalRequests.add(1);
    if (!success) totalErrors.add(1);
  });
}

export function teardown(data) {
  if (!data?.startTime) return;
  const elapsed = Math.round((Date.now() - data.startTime) / 1000);
  console.log(`\n===== 10K TPS Validation Completed (${Math.floor(elapsed / 60)}m ${elapsed % 60}s) =====\n`);
}

export function handleSummary(data) {
  const totalReqs = data.metrics.total_requests?.values?.count || 0;
  const totalErrs = data.metrics.total_errors?.values?.count || 0;
  const pureTPS = totalReqs / TEST_DURATION_SEC;
  const tpsHit = pureTPS >= TARGET_TPS ? '✅' : '❌';

  console.log('\n===== 10K TPS Summary =====');
  console.log(`Target TPS: ${TARGET_TPS}`);
  console.log(`Pure Test TPS: ${pureTPS.toFixed(2)} ${tpsHit}`);
  console.log(`Total Requests: ${totalReqs}`);
  console.log(`Total Errors: ${totalErrs} (${((totalErrs / Math.max(totalReqs, 1)) * 100).toFixed(2)}%)`);
  console.log('===========================\n');

  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'summary-10k.html': htmlReport(data),
    'tps-10k-result.json': JSON.stringify({
      targetTPS: TARGET_TPS,
      achievedTPS: pureTPS,
      totalRequests: totalReqs,
      totalErrors: totalErrs,
      timestamp: new Date().toISOString(),
    }, null, 2),
  };
}
