import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * End-to-End Load Test
 *
 * 전체 플로우 시뮬레이션:
 * 1. URL 생성 (URL Service)
 * 2. 리다이렉션 (Redirect Service)
 * 3. 통계 조회 (Click Service)
 *
 * 목표:
 * - 혼합 워크로드 처리 (생성 10% / 리다이렉션 85% / 조회 5%)
 * - 전체 처리량: 5,000 TPS 이상
 * - 에러율: 1% 이하
 */

// 커스텀 메트릭
const shortenSuccess = new Rate('shorten_success');
const redirectSuccess = new Rate('redirect_success');
const statsSuccess = new Rate('stats_success');
const totalErrors = new Counter('total_errors');

const shortenDuration = new Trend('shorten_duration');
const redirectDuration = new Trend('redirect_duration');
const statsDuration = new Trend('stats_duration');

// 테스트 설정
export const options = {
  scenarios: {
    // 시나리오 1: URL 생성 (10%)
    url_creation: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '2m', target: 100 },
        { duration: '3m', target: 200 },
        { duration: '2m', target: 200 },
        { duration: '1m', target: 0 },
      ],
      exec: 'shortenUrl',
    },

    // 시나리오 2: 리다이렉션 (85%)
    redirection: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 400 },
        { duration: '2m', target: 850 },
        { duration: '3m', target: 1700 },
        { duration: '2m', target: 1700 },
        { duration: '1m', target: 0 },
      ],
      exec: 'redirect',
    },

    // 시나리오 3: 통계 조회 (5%)
    statistics: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 25 },
        { duration: '2m', target: 50 },
        { duration: '3m', target: 100 },
        { duration: '2m', target: 100 },
        { duration: '1m', target: 0 },
      ],
      exec: 'getStats',
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<500'],
    'http_req_failed': ['rate<0.01'],
    'shorten_success': ['rate>0.99'],
    'redirect_success': ['rate>0.99'],
    'stats_success': ['rate>0.95'],
  },
};

const URL_SERVICE = __ENV.URL_SERVICE || 'http://localhost:8081';
const REDIRECT_SERVICE = __ENV.REDIRECT_SERVICE || 'http://localhost:8082';
const CLICK_SERVICE = __ENV.CLICK_SERVICE || 'http://localhost:8083';

// 공유 단축 코드 저장소
const shortCodes = [];
const maxCodes = 1000;

function generateRandomUrl() {
  const timestamp = Date.now();
  const random = Math.floor(Math.random() * 1000000);
  return `https://example.com/e2e/${timestamp}/${random}`;
}

// 시나리오 1: URL 단축
export function shortenUrl() {
  group('URL Shortening', function () {
    const url = generateRandomUrl();
    const payload = JSON.stringify({ originalUrl: url });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const response = http.post(
      `${URL_SERVICE}/api/v1/urls/shorten`,
      payload,
      params
    );

    const success = check(response, {
      'shorten: status is 200': (r) => r.status === 200,
      'shorten: has shortCode': (r) => {
        try {
          const body = JSON.parse(r.body);
          if (body.shortCode) {
            // 단축 코드 저장 (다른 시나리오에서 사용)
            if (shortCodes.length < maxCodes) {
              shortCodes.push(body.shortCode);
            }
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

    sleep(1);
  });
}

// 시나리오 2: 리다이렉션
export function redirect() {
  group('Redirection', function () {
    // 생성된 단축 코드가 없으면 기본 코드 사용
    const shortCode = shortCodes.length > 0
      ? shortCodes[Math.floor(Math.random() * shortCodes.length)]
      : 'abc123'; // 기본 코드 (사전 생성 필요)

    const params = { redirects: 0 };

    const response = http.get(
      `${REDIRECT_SERVICE}/r/${shortCode}`,
      params
    );

    const success = check(response, {
      'redirect: status is 302': (r) => r.status === 302,
      'redirect: has Location': (r) => r.headers['Location'] !== undefined,
    });

    redirectSuccess.add(success);
    redirectDuration.add(response.timings.duration);

    if (!success) {
      totalErrors.add(1);
    }

    sleep(0.5);
  });
}

// 시나리오 3: 통계 조회
export function getStats() {
  group('Statistics Query', function () {
    // 생성된 단축 코드가 없으면 기본 코드 사용
    const shortCode = shortCodes.length > 0
      ? shortCodes[Math.floor(Math.random() * shortCodes.length)]
      : 'abc123'; // 기본 코드

    const response = http.get(
      `${CLICK_SERVICE}/api/v1/clicks/${shortCode}/stats`
    );

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

    sleep(2);
  });
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'e2e-summary.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data, options) {
  const indent = options.indent || '';

  let summary = '\n' + indent + '='.repeat(70) + '\n';
  summary += indent + 'End-to-End Load Test Summary\n';
  summary += indent + '='.repeat(70) + '\n\n';

  // 전체 통계
  const requests = data.metrics.http_reqs.values.count;
  const duration = data.state.testRunDurationMs / 1000;
  const rps = requests / duration;

  summary += indent + `Total Requests: ${requests}\n`;
  summary += indent + `Test Duration: ${duration.toFixed(2)}s\n`;
  summary += indent + `Overall RPS: ${rps.toFixed(2)}\n\n`;

  // 시나리오별 성공률
  summary += indent + 'Success Rates by Scenario:\n';
  if (data.metrics.shorten_success) {
    summary += indent + `  URL Shortening: ${(data.metrics.shorten_success.values.rate * 100).toFixed(2)}%\n`;
  }
  if (data.metrics.redirect_success) {
    summary += indent + `  Redirection: ${(data.metrics.redirect_success.values.rate * 100).toFixed(2)}%\n`;
  }
  if (data.metrics.stats_success) {
    summary += indent + `  Statistics: ${(data.metrics.stats_success.values.rate * 100).toFixed(2)}%\n`;
  }
  summary += '\n';

  // 응답 시간 (전체)
  const httpDuration = data.metrics.http_req_duration.values;
  summary += indent + 'Overall Response Time:\n';
  summary += indent + `  avg: ${httpDuration.avg.toFixed(2)}ms\n`;
  summary += indent + `  p50: ${httpDuration['p(50)'].toFixed(2)}ms\n`;
  summary += indent + `  p90: ${httpDuration['p(90)'].toFixed(2)}ms\n`;
  summary += indent + `  p95: ${httpDuration['p(95)'].toFixed(2)}ms\n`;
  summary += indent + `  p99: ${httpDuration['p(99)'].toFixed(2)}ms\n\n`;

  // 시나리오별 응답 시간
  summary += indent + 'Response Time by Scenario:\n';
  if (data.metrics.shorten_duration) {
    summary += indent + `  Shorten avg: ${data.metrics.shorten_duration.values.avg.toFixed(2)}ms\n`;
  }
  if (data.metrics.redirect_duration) {
    summary += indent + `  Redirect avg: ${data.metrics.redirect_duration.values.avg.toFixed(2)}ms\n`;
  }
  if (data.metrics.stats_duration) {
    summary += indent + `  Stats avg: ${data.metrics.stats_duration.values.avg.toFixed(2)}ms\n`;
  }
  summary += '\n';

  // 에러
  if (data.metrics.total_errors) {
    summary += indent + `Total Errors: ${data.metrics.total_errors.values.count}\n`;
  }

  summary += indent + '='.repeat(70) + '\n';

  return summary;
}
