import http from 'k6/http';
import { check } from 'k6';

/**
 * Smoke Test
 *
 * 목적: 서비스의 정상 동작 확인
 *
 * 부하: 최소
 */

export const options = {
  scenarios: {
    smoke_test: {
      executor: 'constant-arrival-rate',
      rate: 10,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 5,
      maxVUs: 20,
    },
  },
  thresholds: {
    'http_req_duration': ['p(95)<1000'],
    'http_req_failed': ['rate<0.05'],
  },
};

const URL_SERVICE = __ENV.URL_SERVICE || 'http://localhost:8081';
const REDIRECT_SERVICE = __ENV.REDIRECT_SERVICE || 'http://localhost:8082';
const CLICK_SERVICE = __ENV.CLICK_SERVICE || 'http://localhost:8083';

let globalShortCodes = [];

export default function () {
  const testType = Math.random();

  if (testType < 0.33 || globalShortCodes.length === 0) {
    // URL 생성 테스트 (33%)
    const shortenPayload = JSON.stringify({
      originalUrl: `https://example.com/smoke-test/${Date.now()}-${__VU}-${__ITER}`,
    });

    const shortenRes = http.post(
      `${URL_SERVICE}/api/v1/urls/shorten`,
      shortenPayload,
      { headers: { 'Content-Type': 'application/json' } }
    );

    const shortenOk = check(shortenRes, {
      'URL Service is up': (r) => r.status === 200,
      'Short code created': (r) => {
        try {
          const body = JSON.parse(r.body);
          if (body.shortCode) {
            globalShortCodes.push(body.shortCode);
            if (globalShortCodes.length > 50) {
              globalShortCodes = globalShortCodes.slice(-50);
            }
            return true;
          }
          return false;
        } catch (e) {
          return false;
        }
      },
    });
  } else if (testType < 0.66 && globalShortCodes.length > 0) {
    // 리다이렉션 테스트 (33%)
    const shortCode = globalShortCodes[Math.floor(Math.random() * globalShortCodes.length)];
    const redirectRes = http.get(
      `${REDIRECT_SERVICE}/r/${shortCode}`,
      { redirects: 0 }
    );

    check(redirectRes, {
      'Redirect Service is up': (r) => r.status === 302 || r.status === 404,
    });
  } else if (globalShortCodes.length > 0) {
    // 클릭 통계 테스트 (33%)
    const shortCode = globalShortCodes[Math.floor(Math.random() * globalShortCodes.length)];
    const statsRes = http.get(
      `${CLICK_SERVICE}/api/v1/analytics/${shortCode}/stats`
    );

    check(statsRes, {
      'Click Service is up': (r) => r.status === 200,
    });
  }
}

export function handleSummary(data) {
  console.log('\n========== Smoke Test Summary ==========');
  console.log(`Duration: ${(data.state.testRunDurationMs / 1000).toFixed(2)}s`);
  console.log(`Total Requests: ${data.metrics.http_reqs.values.count}`);
  console.log(`Failed Requests: ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`);
  console.log(`Avg Response Time: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`);
  console.log('========================================\n');

  return {
    'stdout': '',
  };
}
