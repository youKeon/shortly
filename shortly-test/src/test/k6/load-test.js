import http from 'k6/http';
import {check, sleep, group} from 'k6';
import {Rate, Trend, Counter} from 'k6/metrics';

// Metrics
const shortenSuccess = new Rate('shorten_success');
const redirectSuccess = new Rate('redirect_success');
const statsSuccess = new Rate('stats_success');
const totalErrors = new Counter('total_errors');
const shortenDuration = new Trend('shorten_duration');
const redirectDuration = new Trend('redirect_duration');
const statsDuration = new Trend('stats_duration');

// Test configuration: 8% URL creation, 90% redirect, 2% stats
export const options = {
  scenarios: {
    url_creation: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        {duration: '1m', target: 20},
        {duration: '2m', target: 40},
        {duration: '3m', target: 160},
        {duration: '2m', target: 160},
        {duration: '1m', target: 0},
      ],
      exec: 'shortenUrl',
    },
    redirection: {
      executor: 'ramping-vus',
      startVUs: 0,
      startTime: '10s',
      stages: [
        {duration: '50s', target: 500},
        {duration: '2m', target: 1000},
        {duration: '3m', target: 1800},
        {duration: '2m', target: 1800},
        {duration: '1m', target: 0},
      ],
      exec: 'redirect',
    },
    statistics: {
      executor: 'ramping-vus',
      startVUs: 0,
      startTime: '20s',
      stages: [
        {duration: '40s', target: 10},
        {duration: '2m', target: 20},
        {duration: '3m', target: 40},
        {duration: '2m', target: 40},
        {duration: '1m', target: 0},
      ],
      exec: 'getStats',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<1000'],
    'http_req_failed': ['rate<0.05'],
    'shorten_success': ['rate>0.95'],
    'redirect_success': ['rate>0.95'],
    'stats_success': ['rate>0.90'],
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
  const seedCount = 100;

  for (let i = 0; i < seedCount; i++) {
    const url = getRandomUrl();
    const payload = JSON.stringify({originalUrl: url});
    const params = {headers: {'Content-Type': 'application/json'}};

    const response = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload,
      params);

    if (response.status === 200 || response.status === 201) {
      const body = JSON.parse(response.body);
      if (body.shortCode) {
        codes.push(body.shortCode);
      }
    }

    if (i % 10 === 0 && i > 0) {
      sleep(0.5);
    }
  }

  if (codes.length < 50) {
    throw new Error(`Setup failed: Only ${codes.length} URLs created`);
  }

  return {shortCodes: codes};
}

export function shortenUrl(data) {
  if (globalShortCodes.length === 0 && data && data.shortCodes) {
    globalShortCodes = data.shortCodes;
  }

  group('URL Shortening', function () {
    const url = getRandomUrl();
    const payload = JSON.stringify({originalUrl: url});
    const params = {headers: {'Content-Type': 'application/json'}};

    const response = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload,
      params);

    const success = check(response, {
      'shorten: status is 200 or 201': (r) => r.status === 200 || r.status
        === 201,
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
    if (!success) {
      totalErrors.add(1);
    }

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

    const shortCode = globalShortCodes[Math.floor(
      Math.random() * globalShortCodes.length)];
    const params = {redirects: 0};

    const response = http.get(`${REDIRECT_SERVICE}/r/${shortCode}`, params);

    const success = check(response, {
      'redirect: status is 302': (r) => r.status === 302,
      'redirect: has Location': (r) => r.headers['Location'] !== undefined,
    });

    redirectSuccess.add(success);
    redirectDuration.add(response.timings.duration);
    if (!success) {
      totalErrors.add(1);
    }

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

    const shortCode = globalShortCodes[Math.floor(
      Math.random() * globalShortCodes.length)];

    const response = http.get(
      `${CLICK_SERVICE}/api/v1/analytics/${shortCode}/stats`);

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
    if (!success) {
      totalErrors.add(1);
    }

    sleep(Math.random() * 3 + 2);
  });
}
