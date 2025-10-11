import http from 'k6/http';
import {check} from 'k6';
import {Rate} from 'k6/metrics';

// 커스텀 메트릭
const redirectSuccessRate = new Rate('redirect_success_rate');
const shortenSuccessRate = new Rate('shorten_success_rate');

export const options = {
  stages: [
    {duration: '1m', target: 100},
    {duration: '2m', target: 500},
    {duration: '3m', target: 500},
    {duration: '1m', target: 0},
  ],
  thresholds: {
    'http_req_failed': ['rate<0.01'],           // 에러율 < 1%
    'redirect_success_rate': ['rate>0.99'],     // 리디렉션 성공률 > 99%
    'shorten_success_rate': ['rate>0.99'],      // 단축 성공률 > 99%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

let shortCodes = [];

export function setup() {
  console.log('=========================================================');
  console.log('부하 테스트 시작');
  console.log('=========================================================');

  const warmupCodes = [];
  for (let i = 0; i < 100; i++) {
    const payload = JSON.stringify({
      originalUrl: `https://example.com/warmup/${i}`
    });

    const res = http.post(`${BASE_URL}/api/v1/urls/shorten`, payload, {
      headers: {'Content-Type': 'application/json'},
    });

    if (res.status === 200) {
      const data = JSON.parse(res.body);
      warmupCodes.push(data.shortCode);
    }

    if ((i + 1) % 25 === 0) {
      console.log(`  진행: ${i + 1}/100`);
    }
  }
  return {shortCodes: warmupCodes};
}

export default function (data) {
  // 트래픽 비율: 리디렉션(90%), 단축(10%)
  const isRedirect = Math.random() < 0.9;

  if (isRedirect && data.shortCodes.length > 0) {
    // 리디렉션 테스트 (90%)
    const shortCode = data.shortCodes[Math.floor(
      Math.random() * data.shortCodes.length)];

    const res = http.get(`${BASE_URL}/api/v1/urls/${shortCode}`, {
      redirects: 0,  // 302 응답만 받고 실제 리디렉션은 안 함
    });

    const success = check(res, {
      'redirect: status is 302': (r) => r.status === 302,
      'redirect: has Location header': (r) => r.headers['Location']
        !== undefined,
    });

    redirectSuccessRate.add(success);

  } else {
    // URL 단축 테스트 (10%)
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

    // 성공한 경우 풀에 추가
    if (success) {
      const body = JSON.parse(res.body);
      data.shortCodes.push(body.shortCode);

      // 풀 크기 제한 (메모리 관리)
      if (data.shortCodes.length > 1000) {
        data.shortCodes = data.shortCodes.slice(-1000);
      }
    }
  }
}

export function teardown(data) {
  console.log('');
  console.log('=========================================================');
  console.log('부하 테스트 완료');
  console.log('=========================================================');
  console.log('');
}
