import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// 커스텀 메트릭
const shortenErrors = new Counter('shorten_errors');
const redirectErrors = new Counter('redirect_errors');
const shortenSuccessRate = new Rate('shorten_success_rate');
const redirectSuccessRate = new Rate('redirect_success_rate');
const shortenDuration = new Trend('shorten_duration');
const redirectDuration = new Trend('redirect_duration');

// Phase 1 목표 설정
export const options = {
  stages: [
    { duration: '2m', target: 100 },   // 램프업: 100 VUs
    { duration: '5m', target: 200 },   // Phase 1 목표: 200 VUs
    { duration: '2m', target: 0 },     // 램프다운
  ],
  thresholds: {
    // Phase 1 성공 기준
    'http_req_duration': ['p(95)<100'],           // P95 < 100ms
    'http_req_failed': ['rate<0.05'],             // 실패율 < 5%
    'http_reqs': ['rate>1000'],                   // TPS ≥ 1000

    // 개별 API 기준
    'shorten_duration': ['p(95)<150'],            // URL 단축 P95 < 150ms
    'redirect_duration': ['p(95)<50'],            // 리디렉션 P95 < 50ms (인덱스 효과 확인)
    'shorten_success_rate': ['rate>0.95'],        // 단축 성공률 > 95%
    'redirect_success_rate': ['rate>0.95'],       // 리디렉션 성공률 > 95%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트용 짧은 코드 풀 (리디렉션 테스트용)
let shortCodes = [];

export function setup() {
  console.log('========================== Phase 1 성능 테스트 시작 ========================== ');
  console.log('목표: TPS 1,000, 200 VUs, P95 < 100ms');
  console.log('변경 사항: DB 인덱스 추가, Connection Pool & Tomcat 튜닝');

  // 초기 짧은 URL 생성 (워밍업)
  const warmupCodes = [];
  for (let i = 0; i < 100; i++) {
    const payload = JSON.stringify({
      originalUrl: `https://example.com/warmup/${i}`
    });

    const res = http.post(`${BASE_URL}/api/v1/urls/shorten`, payload, {
      headers: { 'Content-Type': 'application/json' },
    });

    if (res.status === 200) {
      const data = JSON.parse(res.body);
      warmupCodes.push(data.shortCode);
    }
  }

  return { shortCodes: warmupCodes };
}

export default function (data) {
  // 리디렉션(80%), 단축(20%)
  const isRedirect = Math.random() < 0.8;

  if (isRedirect && data.shortCodes.length > 0) {
    // 리디렉션 테스트
    const shortCode = data.shortCodes[Math.floor(Math.random() * data.shortCodes.length)];

    const startTime = new Date().getTime();
    const res = http.get(`${BASE_URL}/api/v1/urls/${shortCode}`, {
      redirects: 0,
    });
    const duration = new Date().getTime() - startTime;

    redirectDuration.add(duration);

    const success = check(res, {
      'redirect: status is 302': (r) => r.status === 302,
      'redirect: has Location header': (r) => r.headers['Location'] !== undefined,
    });

    redirectSuccessRate.add(success);
    if (!success) {
      redirectErrors.add(1);
      console.error(`[ERROR] ::: Redirect failed for ${shortCode}: ${res.status}`);
    }

  } else {
    // URL 단축
    const uniqueUrl = `https://example.com/test/${__VU}-${__ITER}-${Date.now()}`;
    const payload = JSON.stringify({
      originalUrl: uniqueUrl
    });

    const startTime = new Date().getTime();
    const res = http.post(`${BASE_URL}/api/v1/urls/shorten`, payload, {
      headers: { 'Content-Type': 'application/json' },
    });
    const duration = new Date().getTime() - startTime;

    shortenDuration.add(duration);

    const success = check(res, {
      'shorten: status is 200': (r) => r.status === 200,
      'shorten: has shortCode': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.shortCode !== undefined && body.shortCode.length > 0;
        } catch (e) {
          return false;
        }
      },
    });

    shortenSuccessRate.add(success);

    if (success) {
      const body = JSON.parse(res.body);
      data.shortCodes.push(body.shortCode);

      // 풀 크기 제한 (메모리 관리)
      if (data.shortCodes.length > 1000) {
        data.shortCodes = data.shortCodes.slice(-1000);
      }
    } else {
      shortenErrors.add(1);
      console.error(`[ERROR] ::: Shorten failed: ${res.status} - ${res.body}`);
    }
  }
}

export function teardown(data) {
  console.log('\n Phase 1 테스트 완료');
  console.log('='.repeat(50));
  console.log(`총 생성된 URL: ${data.shortCodes.length}개`);
  console.log('\n 결과 분석:');
  console.log('- http_req_duration p(95): 목표 < 100ms');
  console.log('- http_req_failed rate: 목표 < 5%');
  console.log('- TPS: 목표 ≥ 1000');
  console.log('- redirect_duration p(95): 목표 < 50ms (인덱스 효과)');
}

