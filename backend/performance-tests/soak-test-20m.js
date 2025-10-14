import http from 'k6/http';
import {check} from 'k6';
import {Rate, Counter, Trend} from 'k6/metrics';

const redirectSuccessRate = new Rate('redirect_success_rate');
const shortenSuccessRate = new Rate('shorten_success_rate');
const redirectCount = new Counter('redirect_count');
const shortenCount = new Counter('shorten_count');
const memoryPressure = new Trend('memory_pressure_indicator');

export const options = {
  stages: [
    {duration: '2m', target: 500},      // Warm-up
    {duration: '3m', target: 1200},     // Ramp-up to Peak
    {duration: '20m', target: 1200},    // Soak (20분 지속) - 핵심 구간
    {duration: '2m', target: 0},        // Ramp-down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<300'],           // Soak에서는 여유있게 설정
    'http_req_failed': ['rate<0.01'],             // 에러율 < 1%
    'http_reqs': ['rate>10000'],                  // TPS > 10,000
    'redirect_success_rate': ['rate>0.98'],       // 리디렉션 성공률 > 98%
    'shorten_success_rate': ['rate>0.98'],        // 단축 성공률 > 98%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

let shortCodes = [];

export function setup() {
  console.log('');
  console.log('=========================================================');
  console.log('SOAK TEST - 20분 지속 부하 테스트');
  console.log('=========================================================');
  console.log('목적: 장시간 운영 시 안정성 검증');
  console.log('  - 메모리 누수 탐지');
  console.log('  - GC 압력 모니터링');
  console.log('  - Connection Pool 안정성');
  console.log('  - Cache 효율 지속성');
  console.log('');
  console.log('트래픽 비율: URL 단축 10%, 리다이렉팅 90%');
  console.log('목표 TPS: 10,000+');
  console.log('최대 VU: 1,200');
  console.log('총 테스트 시간: 27분 (Soak 20분 포함)');
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
  console.log(`Warm-up 완료: ${warmupCodes.length}개 shortCode 생성`);
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
    
    // 응답 시간으로 시스템 부하 간접 측정
    memoryPressure.add(res.timings.duration);

  } else {
    const uniqueUrl = `https://example.com/soak/${__VU}-${__ITER}-${Date.now()}`;
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

      // 메모리 압박 방지: shortCode 풀 크기 제한
      if (data.shortCodes.length > 5000) {
        data.shortCodes = data.shortCodes.slice(-5000);
      }
    }
    
    memoryPressure.add(res.timings.duration);
  }
}

export function teardown(data) {
  console.log('');
  console.log('=========================================================');
  console.log('SOAK TEST 완료 - 20분 지속 부하 테스트');
  console.log('=========================================================');
  console.log(`최종 shortCode 풀 크기: ${data.shortCodes.length}`);
  console.log('');
  console.log('주요 확인 사항:');
  console.log('  1. TPS가 20분 동안 일정하게 유지되었는가?');
  console.log('  2. P95 응답시간이 시간에 따라 증가하지 않았는가?');
  console.log('  3. 에러율이 0%에 가깝게 유지되었는가?');
  console.log('  4. 메모리 누수 징후가 없는가? (응답시간 증가)');
  console.log('');
  console.log('다음 단계:');
  console.log('  - JVM 메모리 그래프 확인 (힙 사용량)');
  console.log('  - GC 로그 분석 (Full GC 발생 여부)');
  console.log('  - Connection Pool 메트릭 확인');
  console.log('  - Cache 히트율 추이 확인');
  console.log('=========================================================');
  console.log('');
}

