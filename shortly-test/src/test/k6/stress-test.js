import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Metrics
const shortenSuccess = new Rate('shorten_success');
const redirectSuccess = new Rate('redirect_success');
const statsSuccess = new Rate('stats_success');
const totalErrors = new Counter('total_errors');
const shortenDuration = new Trend('shorten_duration');
const redirectDuration = new Trend('redirect_duration');
const statsDuration = new Trend('stats_duration');

// Progressive Stress Test: Gradually increase load to find breaking point
// Maintains 8% / 90% / 2% ratio across all stages
export const options = {
  scenarios: {
    url_creation: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        // Stage 1: Warmup (baseline)
        { duration: '1m', target: 20 },
        { duration: '2m', target: 20 },

        // Stage 2: Low load
        { duration: '1m', target: 50 },
        { duration: '2m', target: 50 },

        // Stage 3: Medium load
        { duration: '1m', target: 100 },
        { duration: '2m', target: 100 },

        // Stage 4: High load
        { duration: '1m', target: 200 },
        { duration: '2m', target: 200 },

        // Stage 5: Very high load
        { duration: '1m', target: 300 },
        { duration: '2m', target: 300 },

        // Stage 6: Maximum load
        { duration: '1m', target: 400 },
        { duration: '2m', target: 400 },

        // Cooldown
        { duration: '1m', target: 0 },
      ],
      exec: 'shortenUrl',
    },
    redirection: {
      executor: 'ramping-vus',
      startVUs: 0,
      startTime: '10s',
      stages: [
        // Stage 1: Warmup
        { duration: '50s', target: 500 },
        { duration: '2m', target: 500 },

        // Stage 2: Low load
        { duration: '1m', target: 1000 },
        { duration: '2m', target: 1000 },

        // Stage 3: Medium load
        { duration: '1m', target: 2000 },
        { duration: '2m', target: 2000 },

        // Stage 4: High load
        { duration: '1m', target: 3000 },
        { duration: '2m', target: 3000 },

        // Stage 5: Very high load
        { duration: '1m', target: 4000 },
        { duration: '2m', target: 4000 },

        // Stage 6: Maximum load
        { duration: '1m', target: 5000 },
        { duration: '2m', target: 5000 },

        // Cooldown
        { duration: '1m', target: 0 },
      ],
      exec: 'redirect',
    },
    statistics: {
      executor: 'ramping-vus',
      startVUs: 0,
      startTime: '20s',
      stages: [
        // Stage 1: Warmup
        { duration: '40s', target: 10 },
        { duration: '2m', target: 10 },

        // Stage 2: Low load
        { duration: '1m', target: 20 },
        { duration: '2m', target: 20 },

        // Stage 3: Medium load
        { duration: '1m', target: 40 },
        { duration: '2m', target: 40 },

        // Stage 4: High load
        { duration: '1m', target: 60 },
        { duration: '2m', target: 60 },

        // Stage 5: Very high load
        { duration: '1m', target: 80 },
        { duration: '2m', target: 80 },

        // Stage 6: Maximum load
        { duration: '1m', target: 100 },
        { duration: '2m', target: 100 },

        // Cooldown
        { duration: '1m', target: 0 },
      ],
      exec: 'getStats',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<1000', 'p(99)<2000'],
    'http_req_failed': ['rate<0.10'],  // Allow up to 10% errors during stress test
    'shorten_success': ['rate>0.90'],
    'redirect_success': ['rate>0.90'],
    'stats_success': ['rate>0.85'],
  },
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
  'https://techcrunch.com',
  'https://theverge.com',
  'https://arstechnica.com',
  'https://wired.com',
  'https://cnet.com',
  'https://bbc.com',
  'https://cnn.com',
  'https://nytimes.com',
  'https://wikipedia.org',
  'https://amazon.com',
];

let globalShortCodes = [];

function getRandomUrl() {
  return POPULAR_SITES[Math.floor(Math.random() * POPULAR_SITES.length)];
}

export function setup() {
  const healthCheck = http.get(`${URL_SERVICE}/actuator/health`);
  if (healthCheck.status !== 200) {
    throw new Error('URL Service is not ready');
  }

  const codes = [];
  const seedCount = 200;  // More seeds for stress test

  console.log(`Seeding ${seedCount} URLs for stress test...`);

  for (let i = 0; i < seedCount; i++) {
    const url = getRandomUrl();
    const payload = JSON.stringify({ originalUrl: url });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const response = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload, params);

    if (response.status === 200 || response.status === 201) {
      const body = JSON.parse(response.body);
      if (body.shortCode) {
        codes.push(body.shortCode);
      }
    }

    if (i % 20 === 0 && i > 0) {
      sleep(0.5);
    }
  }

  if (codes.length < 100) {
    throw new Error(`Setup failed: Only ${codes.length} URLs created`);
  }

  console.log(`Setup complete: ${codes.length} URLs ready for stress test`);
  return { shortCodes: codes };
}

export function shortenUrl(data) {
  if (globalShortCodes.length === 0 && data && data.shortCodes) {
    globalShortCodes = data.shortCodes;
  }

  group('URL Shortening', function () {
    const url = getRandomUrl();
    const payload = JSON.stringify({ originalUrl: url });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const response = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload, params);

    const success = check(response, {
      'shorten: status is 200 or 201': (r) => r.status === 200 || r.status === 201,
      'shorten: has shortCode': (r) => {
        try {
          const body = JSON.parse(r.body);
          if (body.shortCode) {
            globalShortCodes.push(body.shortCode);
            return true;
          }
          return false;
        } catch (e) {
          return false;
        }
      },
    });

    shortenSuccess.add(success);
    shortenDuration.add(response.timings.duration);
    if (!success) totalErrors.add(1);

    sleep(Math.random() * 2 + 1);
  });
}

export function redirect(data) {
  if (globalShortCodes.length === 0 && data && data.shortCodes) {
    globalShortCodes = data.shortCodes;
  }

  group('Redirection', function () {
    if (globalShortCodes.length === 0) {
      totalErrors.add(1);
      sleep(5);
      return;
    }

    const shortCode = globalShortCodes[Math.floor(Math.random() * globalShortCodes.length)];
    const params = { redirects: 0 };

    const response = http.get(`${REDIRECT_SERVICE}/r/${shortCode}`, params);

    const success = check(response, {
      'redirect: status is 302': (r) => r.status === 302,
      'redirect: has Location': (r) => r.headers['Location'] !== undefined,
    });

    redirectSuccess.add(success);
    redirectDuration.add(response.timings.duration);
    if (!success) totalErrors.add(1);

    sleep(Math.random() * 0.5);
  });
}

export function getStats(data) {
  if (globalShortCodes.length === 0 && data && data.shortCodes) {
    globalShortCodes = data.shortCodes;
  }

  group('Statistics Query', function () {
    if (globalShortCodes.length === 0) {
      totalErrors.add(1);
      sleep(5);
      return;
    }

    const shortCode = globalShortCodes[Math.floor(Math.random() * globalShortCodes.length)];

    const response = http.get(`${CLICK_SERVICE}/api/v1/analytics/${shortCode}/stats`);

    const success = check(response, {
      'stats: status is 200': (r) => r.status === 200,
      'stats: has totalClicks': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.totalClicks !== undefined;
        } catch (e) {
          return false;
        }
      },
    });

    statsSuccess.add(success);
    statsDuration.add(response.timings.duration);
    if (!success) totalErrors.add(1);

    sleep(Math.random() * 3 + 2);
  });
}
