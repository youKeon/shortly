import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * URL Service 성능 테스트
 *
 * 목표:
 * - 처리량: 1,000 TPS 이상
 * - P95 레이턴시: 200ms 이하
 * - 에러율: 1% 이하
 */

// 커스텀 메트릭
const errorRate = new Rate('errors');
const successRate = new Rate('success');
const shortenDuration = new Trend('shorten_duration');
const shortCodeGenerated = new Counter('short_codes_generated');

// 테스트 설정
export const options = {
  stages: [
    { duration: '30s', target: 100 },   // Warm-up: 0 -> 100 VUs
    { duration: '1m', target: 500 },    // Ramp-up: 100 -> 500 VUs
    { duration: '3m', target: 1000 },   // Peak load: 500 -> 1000 VUs
    { duration: '2m', target: 1000 },   // Sustain: 1000 VUs
    { duration: '1m', target: 0 },      // Ramp-down: 1000 -> 0
  ],
  thresholds: {
    'http_req_duration': ['p(95)<200'],  // P95 < 200ms
    'http_req_failed': ['rate<0.01'],    // 에러율 < 1%
    'errors': ['rate<0.01'],
    'success': ['rate>0.99'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// 테스트 데이터 생성
function generateRandomUrl() {
  const timestamp = Date.now();
  const random = Math.floor(Math.random() * 1000000);
  return `https://example.com/test/${timestamp}/${random}`;
}

export default function () {
  // URL 단축 요청
  const url = generateRandomUrl();
  const payload = JSON.stringify({
    originalUrl: url,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  const response = http.post(
    `${BASE_URL}/api/v1/urls/shorten`,
    payload,
    params
  );

  // 응답 검증
  const success = check(response, {
    'status is 200': (r) => r.status === 200,
    'has shortCode': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.shortCode && body.shortCode.length === 6;
      } catch (e) {
        return false;
      }
    },
    'shortCode is base62': (r) => {
      try {
        const body = JSON.parse(r.body);
        return /^[0-9A-Za-z]{6}$/.test(body.shortCode);
      } catch (e) {
        return false;
      }
    },
    'response time < 200ms': (r) => r.timings.duration < 200,
  });

  // 메트릭 기록
  errorRate.add(!success);
  successRate.add(success);
  shortenDuration.add(response.timings.duration);

  if (success) {
    shortCodeGenerated.add(1);
  }

  // Think time (사용자 행동 시뮬레이션)
  sleep(Math.random() * 2); // 0-2초 랜덤 대기
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'summary.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data, options) {
  const indent = options.indent || '';
  const enableColors = options.enableColors || false;

  let summary = '\n' + indent + '='.repeat(60) + '\n';
  summary += indent + 'URL Service Load Test Summary\n';
  summary += indent + '='.repeat(60) + '\n\n';

  // 전체 요청 통계
  const requests = data.metrics.http_reqs.values.count;
  const duration = data.state.testRunDurationMs / 1000;
  const rps = requests / duration;

  summary += indent + `Total Requests: ${requests}\n`;
  summary += indent + `Test Duration: ${duration.toFixed(2)}s\n`;
  summary += indent + `Requests/sec: ${rps.toFixed(2)}\n\n`;

  // HTTP 응답 시간
  const httpDuration = data.metrics.http_req_duration.values;
  summary += indent + 'HTTP Request Duration:\n';
  summary += indent + `  avg: ${httpDuration.avg.toFixed(2)}ms\n`;
  summary += indent + `  min: ${httpDuration.min.toFixed(2)}ms\n`;
  summary += indent + `  max: ${httpDuration.max.toFixed(2)}ms\n`;
  summary += indent + `  p50: ${httpDuration['p(50)'].toFixed(2)}ms\n`;
  summary += indent + `  p90: ${httpDuration['p(90)'].toFixed(2)}ms\n`;
  summary += indent + `  p95: ${httpDuration['p(95)'].toFixed(2)}ms\n`;
  summary += indent + `  p99: ${httpDuration['p(99)'].toFixed(2)}ms\n\n`;

  // 성공/실패 비율
  const successCount = data.metrics.success?.values.rate || 0;
  const errorCount = data.metrics.errors?.values.rate || 0;

  summary += indent + 'Success/Error Rates:\n';
  summary += indent + `  Success: ${(successCount * 100).toFixed(2)}%\n`;
  summary += indent + `  Errors: ${(errorCount * 100).toFixed(2)}%\n\n`;

  // 커스텀 메트릭
  if (data.metrics.short_codes_generated) {
    summary += indent + `Short Codes Generated: ${data.metrics.short_codes_generated.values.count}\n`;
  }

  summary += indent + '='.repeat(60) + '\n';

  return summary;
}
