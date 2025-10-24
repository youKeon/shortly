import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

/**
 * Redirect Service 성능 테스트
 *
 * 목표:
 * - 처리량: 10,000 TPS 이상 (캐시 히트율 90%)
 * - P95 레이턴시: 50ms 이하 (캐시 히트 시)
 * - 에러율: 0.1% 이하
 */

// 커스텀 메트릭
const errorRate = new Rate('errors');
const successRate = new Rate('success');
const redirectDuration = new Trend('redirect_duration');
const cacheHitRate = new Rate('cache_hits');
const redirectCount = new Counter('redirects_count');

// 테스트 설정
export const options = {
  stages: [
    { duration: '30s', target: 500 },    // Warm-up: 0 -> 500 VUs
    { duration: '1m', target: 2000 },    // Ramp-up: 500 -> 2000 VUs
    { duration: '3m', target: 5000 },    // Peak load: 2000 -> 5000 VUs
    { duration: '2m', target: 5000 },    // Sustain: 5000 VUs
    { duration: '1m', target: 0 },       // Ramp-down: 5000 -> 0
  ],
  thresholds: {
    'http_req_duration': ['p(95)<100'],   // P95 < 100ms (캐시 포함)
    'http_req_failed': ['rate<0.001'],    // 에러율 < 0.1%
    'errors': ['rate<0.001'],
    'success': ['rate>0.999'],
    'cache_hits': ['rate>0.85'],          // 캐시 히트율 > 85%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8082';

// 사전 생성된 단축 코드 풀 (캐시 히트 시뮬레이션)
const SHORT_CODES = [
  'abc123', 'def456', 'ghi789', 'jkl012', 'mno345',
  'pqr678', 'stu901', 'vwx234', 'yza567', 'bcd890',
  'efg123', 'hij456', 'klm789', 'nop012', 'qrs345',
  'tuv678', 'wxy901', 'zab234', 'cde567', 'fgh890',
];

// 핫 코드 (자주 접근되는 코드 - 90% 비중)
const HOT_CODES = SHORT_CODES.slice(0, 5);

// 콜드 코드 (가끔 접근되는 코드 - 10% 비중)
const COLD_CODES = SHORT_CODES.slice(5);

function getShortCode() {
  // 90% 확률로 핫 코드, 10% 확률로 콜드 코드
  const isHot = Math.random() < 0.9;
  const pool = isHot ? HOT_CODES : COLD_CODES;
  return pool[Math.floor(Math.random() * pool.length)];
}

export default function () {
  const shortCode = getShortCode();

  const params = {
    redirects: 0, // 리다이렉트 따라가지 않음 (성능 측정 목적)
  };

  const response = http.get(
    `${BASE_URL}/r/${shortCode}`,
    params
  );

  // 응답 검증
  const success = check(response, {
    'status is 302': (r) => r.status === 302,
    'has Location header': (r) => r.headers['Location'] !== undefined,
    'response time < 100ms': (r) => r.timings.duration < 100,
  });

  // 캐시 히트 판단 (응답 시간 기준)
  const isCacheHit = response.timings.duration < 50;

  // 메트릭 기록
  errorRate.add(!success);
  successRate.add(success);
  redirectDuration.add(response.timings.duration);
  cacheHitRate.add(isCacheHit);

  if (success) {
    redirectCount.add(1);
  }

  // Think time (사용자 행동 시뮬레이션)
  sleep(Math.random() * 0.5); // 0-0.5초 랜덤 대기
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'summary.json': JSON.stringify(data, null, 2),
  };
}

function textSummary(data, options) {
  const indent = options.indent || '';

  let summary = '\n' + indent + '='.repeat(60) + '\n';
  summary += indent + 'Redirect Service Load Test Summary\n';
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

  // 캐시 히트율
  const cacheHit = data.metrics.cache_hits?.values.rate || 0;
  summary += indent + `Cache Hit Rate: ${(cacheHit * 100).toFixed(2)}%\n\n`;

  // 리다이렉션 횟수
  if (data.metrics.redirects_count) {
    summary += indent + `Total Redirects: ${data.metrics.redirects_count.values.count}\n`;
  }

  summary += indent + '='.repeat(60) + '\n';

  return summary;
}
