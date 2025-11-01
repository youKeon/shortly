import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * Smoke Test - 기본 기능 검증
 *
 * 목적: 서비스가 정상 동작하는지 빠르게 확인
 * 부하: 최소 (1-5 VUs, 1분)
 *
 * 실행 방법:
 * - MVC 버전: k6 run smoke-test.js
 * - WebFlux 버전: k6 run -e PROFILE=webflux smoke-test.js
 */

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    'http_req_duration': ['p(95)<1000'], // 관대한 임계값
    'http_req_failed': ['rate<0.05'],     // 5% 미만 에러
  },
};

// Profile 설정 (default: mvc)
const PROFILE = __ENV.PROFILE || 'mvc';

const URL_SERVICE = __ENV.URL_SERVICE || 'http://localhost:8081';
const REDIRECT_SERVICE = __ENV.REDIRECT_SERVICE || 'http://localhost:8082';
const CLICK_SERVICE = __ENV.CLICK_SERVICE || 'http://localhost:8083';

export default function () {
  // 1. URL 생성 테스트
  const shortenPayload = JSON.stringify({
    originalUrl: `https://example.com/smoke-test/${Date.now()}`,
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
        return JSON.parse(r.body).shortCode !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  if (shortenOk) {
    const shortCode = JSON.parse(shortenRes.body).shortCode;

    // 2. 리다이렉션 테스트 (짧은 대기 후)
    sleep(0.5);

    const redirectRes = http.get(
      `${REDIRECT_SERVICE}/r/${shortCode}`,
      { redirects: 0 }
    );

    check(redirectRes, {
      'Redirect Service is up': (r) => r.status === 302 || r.status === 404,
    });

    // 3. 클릭 통계 테스트
    sleep(0.5);

    const statsRes = http.get(
      `${CLICK_SERVICE}/api/v1/clicks/${shortCode}/stats`
    );

    check(statsRes, {
      'Click Service is up': (r) => r.status === 200,
    });
  }

  sleep(1);
}

export function handleSummary(data) {
  console.log('\n========== Smoke Test Summary ==========');
  console.log(`Profile: ${PROFILE.toUpperCase()}`);
  console.log(`Duration: ${(data.state.testRunDurationMs / 1000).toFixed(2)}s`);
  console.log(`Total Requests: ${data.metrics.http_reqs.values.count}`);
  console.log(`Failed Requests: ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)}%`);
  console.log(`Avg Response Time: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`);
  console.log('========================================\n');

  return {
    'stdout': '', // 이미 console.log로 출력했으므로 빈 문자열 반환
  };
}
