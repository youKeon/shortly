import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

const TARGET_TPS = 20000;
const TEST_DURATION_SEC = 600; // 10분

// Metrics
const redirectSuccessRate = new Rate('redirect_success_rate');
const redirectDuration = new Trend('redirect_duration');
const totalRequests = new Counter('total_requests');
const totalErrors = new Counter('total_errors');

export const options = {
  scenarios: {
    redirection: {
      executor: 'constant-arrival-rate',
      rate: TARGET_TPS,
      timeUnit: '1s',
      duration: '10m',
      preAllocatedVUs: 5000,
      maxVUs: 30000,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<150', 'p(99)<300'],
    http_req_failed: ['rate<0.05'],
    redirect_success_rate: ['rate>0.95'],
    redirect_duration: ['p(95)<150', 'p(99)<300'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

const REDIRECT_SERVICE = __ENV.REDIRECT_SERVICE || 'http://localhost:8082';

// Short codes 로드
let shortCodes = [];

if (__ENV.SHORT_CODES) {
  // 환경 변수에서 로드
  shortCodes = __ENV.SHORT_CODES.split(',').map(code => code.trim());
  console.log(`Loaded ${shortCodes.length} short codes from environment variable`);
} else {
  // 기본 샘플 데이터 (실제 테스트 시에는 충분하지 않을 수 있음)
  console.warn('⚠️  SHORT_CODES environment variable not set. Using sample data.');
  console.warn('⚠️  For proper testing, provide codes via: -e SHORT_CODES="code1,code2,..."');
  shortCodes = [
    'abc123', 'def456', 'ghi789', 'jkl012', 'mno345',
    'pqr678', 'stu901', 'vwx234', 'yza567', 'bcd890',
  ];
}

export function setup() {
  console.log('\n===== 20K TPS Redirect-Only Test: Setup Started =====');
  console.log(`Target: ${TARGET_TPS} TPS`);
  console.log(`Duration: 10 minutes`);
  console.log(`Short codes available: ${shortCodes.length}\n`);

  // Health check
  const healthUrl = `${REDIRECT_SERVICE}/actuator/health`;
  console.log(`Checking Redirect Service: ${healthUrl}`);
  const res = http.get(healthUrl);

  if (res.status !== 200) {
    throw new Error(`Redirect Service is not ready (status: ${res.status})`);
  }
  console.log('✓ Redirect Service OK');

  // 최소 short codes 확인
  if (shortCodes.length < 100) {
    console.warn(`⚠️  WARNING: Only ${shortCodes.length} short codes available.`);
    console.warn('⚠️  For realistic testing, provide at least 100-1000 codes.');
  }

  console.log('\n===== Setup Complete. Test Running for 10 minutes =====\n');

  return {
    shortCodes: shortCodes,
    startTime: Date.now()
  };
}

export default function(data) {
  const codes = data?.shortCodes || shortCodes;

  if (codes.length === 0) {
    totalErrors.add(1);
    return;
  }

  // 랜덤하게 short code 선택
  const shortCode = codes[Math.floor(Math.random() * codes.length)];

  // Redirect 요청
  const start = Date.now();
  const res = http.get(`${REDIRECT_SERVICE}/r/${shortCode}`, {
    redirects: 0,  // 302 응답만 받고 실제 리다이렉트는 하지 않음
    timeout: '5s',
  });
  const duration = Date.now() - start;

  // 검증
  const success = check(res, {
    'status is 302': (r) => r.status === 302,
    'has Location header': (r) => r.headers['Location'] !== undefined,
  });

  // Metrics 기록
  redirectSuccessRate.add(success);
  redirectDuration.add(duration);
  totalRequests.add(1);
  if (!success) {
    totalErrors.add(1);
  }
}

export function teardown(data) {
  if (!data?.startTime) return;
  const elapsed = Math.round((Date.now() - data.startTime) / 1000);
  console.log(`\n===== 20K TPS Redirect-Only Test Completed (${Math.floor(elapsed / 60)}m ${elapsed % 60}s) =====\n`);
}

export function handleSummary(data) {
  const totalReqs = data.metrics.total_requests?.values?.count || 0;
  const totalErrs = data.metrics.total_errors?.values?.count || 0;
  const achievedTPS = totalReqs / TEST_DURATION_SEC;
  const tpsHit = achievedTPS >= TARGET_TPS ? '✅' : '❌';

  const p50 = data.metrics.redirect_duration?.values?.['med'] || 0;
  const p95 = data.metrics.redirect_duration?.values?.['p(95)'] || 0;
  const p99 = data.metrics.redirect_duration?.values?.['p(99)'] || 0;
  const avgLatency = data.metrics.redirect_duration?.values?.avg || 0;
  const minLatency = data.metrics.redirect_duration?.values?.min || 0;
  const maxLatency = data.metrics.redirect_duration?.values?.max || 0;

  const successRate = data.metrics.redirect_success_rate?.values?.rate || 0;
  const errorRate = totalErrs / Math.max(totalReqs, 1);

  console.log('\n====================================');
  console.log('  20K TPS REDIRECT-ONLY TEST SUMMARY');
  console.log('====================================');
  console.log(`Target TPS: ${TARGET_TPS.toLocaleString()}`);
  console.log(`Achieved TPS: ${achievedTPS.toFixed(2)} ${tpsHit}`);
  console.log(`Total Requests: ${totalReqs.toLocaleString()}`);
  console.log(`Total Errors: ${totalErrs.toLocaleString()} (${(errorRate * 100).toFixed(2)}%)`);
  console.log('\n--- Latency Performance ---');
  console.log(`Min: ${minLatency.toFixed(2)}ms`);
  console.log(`Avg: ${avgLatency.toFixed(2)}ms`);
  console.log(`P50 (Median): ${p50.toFixed(2)}ms`);
  console.log(`P95: ${p95.toFixed(2)}ms ${p95 < 150 ? '✅' : '❌'}`);
  console.log(`P99: ${p99.toFixed(2)}ms ${p99 < 300 ? '✅' : '❌'}`);
  console.log(`Max: ${maxLatency.toFixed(2)}ms`);
  console.log('\n--- Success Rate ---');
  console.log(`Success Rate: ${(successRate * 100).toFixed(2)}% ${successRate > 0.95 ? '✅' : '❌'}`);
  console.log(`Error Rate: ${(errorRate * 100).toFixed(2)}% ${errorRate < 0.05 ? '✅' : '❌'}`);
  console.log('====================================\n');

  // Pass/Fail 판단
  const passed =
    achievedTPS >= TARGET_TPS &&
    successRate > 0.95 &&
    errorRate < 0.05 &&
    p95 < 150 &&
    p99 < 300;

  if (passed) {
    console.log('✅ TEST PASSED: All criteria met!');
  } else {
    console.log('❌ TEST FAILED: Some criteria not met.');
  }

  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    'summary-20k-redirect-only.html': htmlReport(data),
    'tps-20k-redirect-only-result.json': JSON.stringify({
      targetTPS: TARGET_TPS,
      achievedTPS: achievedTPS,
      totalRequests: totalReqs,
      totalErrors: totalErrs,
      errorRate: errorRate,
      successRate: successRate,
      latency: {
        min: minLatency,
        avg: avgLatency,
        p50: p50,
        p95: p95,
        p99: p99,
        max: maxLatency,
      },
      passed: passed,
      timestamp: new Date().toISOString(),
    }, null, 2),
  };
}
