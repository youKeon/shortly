import http from 'k6/http';
import {check} from 'k6';
import {Rate, Counter} from 'k6/metrics';

const redirectSuccessRate = new Rate('redirect_success_rate');
const shortenSuccessRate = new Rate('shorten_success_rate');
const redirectCount = new Counter('redirect_count');
const shortenCount = new Counter('shorten_count');

export const options = {
  stages: [
    {duration: '30s', target: 500},    // Warm-up
    {duration: '1m', target: 1200},    // Ramp-up to Peak
    {duration: '1m', target: 1200},    // Sustain (10K TPS 측정)
    {duration: '30s', target: 0},      // Ramp-down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<200'],           // P95 < 200ms
    'http_req_failed': ['rate<0.05'],             // 에러율 < 5%
    'http_reqs': ['rate>10000'],                  // TPS > 10,000 🎯
    'redirect_success_rate': ['rate>0.95'],       // 리디렉션 성공률 > 95%
    'shorten_success_rate': ['rate>0.95'],        // 단축 성공률 > 95%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

let shortCodes = [];

export function setup() {
  console.log('');
  console.log('=========================================================');
  console.log('🎯 Phase 1 (Tomcat Only) - 10K TPS 목표 테스트');
  console.log('=========================================================');
  console.log('트래픽 비율: URL 단축 10%, 리다이렉팅 90%');
  console.log('목표 TPS: 10,000+');
  console.log('최대 VU: 1,200');
  console.log('테스트 시간: 3분');
  console.log('=========================================================');
  console.log('');
  console.log('Warm-up 데이터 생성 중...');

  const warmupCodes = [];
  const batchSize = 10;
  const totalBatches = 20; // 총 200개 생성

  for (let j = 0; j < totalBatches; j++) {
    const requests = [];
    for (let i = 0; i < batchSize; i++) {
      const originalUrl = `https://example.com/warmup/${j * batchSize + i}-${Date.now()}`;
      const payload = JSON.stringify({originalUrl: originalUrl});
      requests.push(['POST', `${BASE_URL}/api/v1/urls/shorten`, payload, {
        headers: {'Content-Type': 'application/json'},
      }]);
    }
    const responses = http.batch(requests);
    for (const res of responses) {
      if (res.status === 200) {
        const data = JSON.parse(res.body);
        warmupCodes.push(data.shortCode);
      }
    }
    if ((j + 1) % 5 === 0) {
      console.log(`Warm-up 진행 중: ${warmupCodes.length}/200`);
    }
  }
  console.log(`✅ Warm-up 완료: ${warmupCodes.length}개 shortCode 생성`);
  console.log('');
  return {shortCodes: warmupCodes};
}

export default function (data) {
  const isRedirect = Math.random() < 0.9;

  if (isRedirect && data.shortCodes.length > 0) {
    const shortCode = data.shortCodes[Math.floor(
      Math.random() * data.shortCodes.length)];

    const res = http.get(`${BASE_URL}/api/v1/urls/${shortCode}`, {
      redirects: 0,
    });

    const success = check(res, {
      'redirect: status is 302': (r) => r.status === 302,
      'redirect: has Location header': (r) => r.headers['Location']
        !== undefined,
    });

    redirectSuccessRate.add(success);
    redirectCount.add(1);

  } else {
    const uniqueUrl = `https://example.com/test/${__VU}-${__ITER}-${Date.now()}`;
    const payload = JSON.stringify({
      originalUrl: uniqueUrl
    });

    const res = http.post(`${BASE_URL}/api/v1/urls/shorten`, payload, {
      headers: {'Content-Type': 'application/json'},
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
    shortenCount.add(1);

    if (success) {
      const body = JSON.parse(res.body);
      data.shortCodes.push(body.shortCode);

      if (data.shortCodes.length > 2000) {
        data.shortCodes = data.shortCodes.slice(-2000);
      }
    }
  }
}

export function teardown(data) {
  console.log('');
  console.log('=========================================================');
  console.log('🎯 Phase 1 - 10K TPS 테스트 완료');
  console.log('=========================================================');
  console.log(`최종 shortCode 풀 크기: ${data.shortCodes.length}`);
  console.log('');
  console.log('📊 결과 요약은 위의 Summary를 확인하세요.');
  console.log('');
  console.log('주요 확인 지표:');
  console.log('  - http_reqs: TPS (목표: 10,000+)');
  console.log('  - http_req_duration (p95): P95 응답 시간 (목표: <200ms)');
  console.log('  - http_req_failed: 에러율 (목표: <5%)');
  console.log('  - redirect_success_rate: 리디렉션 성공률 (목표: >95%)');
  console.log('  - shorten_success_rate: 단축 성공률 (목표: >95%)');
  console.log('=========================================================');
  console.log('');
}
