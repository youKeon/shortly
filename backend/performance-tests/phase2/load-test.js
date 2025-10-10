import http from 'k6/http';
import { check } from 'k6';
import { Rate } from 'k6/metrics';

// 커스텀 메트릭 (핵심만)
const shortenSuccessRate = new Rate('shorten_success_rate');
const redirectSuccessRate = new Rate('redirect_success_rate');

// Phase 2 목표 설정
export const options = {
  stages: [
    { duration: '2m', target: 200 },   // 램프업: 200 VUs
    { duration: '5m', target: 500 },   // Phase 2 목표: 500 VUs
    { duration: '2m', target: 0 },     // 램프다운
  ],
  thresholds: {
    // 핵심 성능 지표
    'http_req_duration': ['p(95)<50'],            // P95 < 50ms
    'http_req_failed': ['rate<0.01'],             // 실패율 < 1%
    'http_reqs': ['rate>3000'],                   // TPS ≥ 3000

    // API 안정성
    'shorten_success_rate': ['rate>0.99'],        // 단축 성공률 > 99%
    'redirect_success_rate': ['rate>0.99'],       // 리디렉션 성공률 > 99%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// 테스트용 짧은 코드 풀
let shortCodes = [];

export function setup() {
  console.log('========================== Phase 2 성능 테스트 시작 ==========================');
  console.log('목표: TPS 3,000+, P95 < 50ms, 실패율 < 1%');
  console.log('변경 사항: Redis 캐싱');
  console.log('');

  // 워밍업: 초기 URL 200개 생성
  const warmupCodes = [];
  console.log('워밍업: 초기 URL 200개 생성 중...');

  for (let i = 0; i < 200; i++) {
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

    if ((i + 1) % 50 === 0) {
      console.log(`워밍업 진행: ${i + 1}/200`);
    }
  }

  console.log(`워밍업 완료: ${warmupCodes.length}개 URL 생성됨`);
  console.log('===============================================================================');
  console.log('');

  return { shortCodes: warmupCodes };
}

export default function (data) {
  // 리디렉션(90%), 단축(10%)
  const isRedirect = Math.random() < 0.9;

  if (isRedirect && data.shortCodes.length > 0) {
    // 리디렉션 테스트
    const shortCode = data.shortCodes[Math.floor(Math.random() * data.shortCodes.length)];

    const res = http.get(`${BASE_URL}/api/v1/urls/${shortCode}`, {
      redirects: 0,
    });

    const success = check(res, {
      'redirect: status is 302': (r) => r.status === 302,
      'redirect: has Location header': (r) => r.headers['Location'] !== undefined,
    });

    redirectSuccessRate.add(success);

  } else {
    // URL 단축
    const uniqueUrl = `https://example.com/test/${__VU}-${__ITER}-${Date.now()}`;
    const payload = JSON.stringify({
      originalUrl: uniqueUrl
    });

    const res = http.post(`${BASE_URL}/api/v1/urls/shorten`, payload, {
      headers: { 'Content-Type': 'application/json' },
    });

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
      if (data.shortCodes.length > 2000) {
        data.shortCodes = data.shortCodes.slice(-2000);
      }
    }
  }
}

export function teardown(data) {
  console.log('');
  console.log('================================ Phase 2 테스트 완료 ================================');
  console.log(`총 생성된 URL: ${data.shortCodes.length}개`);
  console.log('');
  console.log('핵심 지표:');
  console.log('- TPS: 목표 ≥ 3,000');
  console.log('- P95 응답시간: 목표 < 50ms');
  console.log('- 실패율: 목표 < 1%');
  console.log('====================================================================================');
}
