import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

/**
 * Phase 1: 1,000 TPS Performance Test
 *
 * 목표: 1,000 TPS, 100만 DAU
 * 시간: 5분
 *
 * 검증 지표:
 * - TPS: 1,000 이상
 * - P95 응답 시간: < 300ms
 * - P99 응답 시간: < 800ms
 * - 에러율: < 3%
 * - 성공률: > 97%
 *
 */

const shortenSuccessRate = new Rate('shorten_success_rate');
const redirectSuccessRate = new Rate('redirect_success_rate');
const statsSuccessRate = new Rate('stats_success_rate');

const shortenDuration = new Trend('shorten_duration');
const redirectDuration = new Trend('redirect_duration');
const statsDuration = new Trend('stats_duration');

const totalRequests = new Counter('total_requests');
const totalErrors = new Counter('total_errors');

export const options = {
  scenarios: {
    // 단축 URL 생성 (8%, 80 TPS)
    url_creation: {
      executor: 'constant-arrival-rate',
      rate: 80,
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 20,
      maxVUs: 100,
      exec: 'shortenUrl',
    },

    // Redirection (90%, 900 TPS)
    redirection: {
      executor: 'constant-arrival-rate',
      rate: 900,
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 200,
      maxVUs: 1000,
      exec: 'redirect',
      startTime: '10s',
    },

    // 클릭 통계 (2%, 20 TPS)
    statistics: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '5m',
      preAllocatedVUs: 10,
      maxVUs: 50,
      exec: 'getStats',
      startTime: '20s',
    },
  },

  thresholds: {
    'http_req_duration': ['p(95)<300', 'p(99)<800'],    // P95 < 300ms, P99 < 800ms
    'http_req_failed': ['rate<0.03'],                   // 에러율 < 3%

    'shorten_success_rate': ['rate>0.97'],              // 단축 성공률 > 97%
    'redirect_success_rate': ['rate>0.97'],             // 리디렉션 성공률 > 97%
    'stats_success_rate': ['rate>0.95'],                // 통계 조회 성공률 > 95%

    'shorten_duration': ['p(95)<250'],                  // 단축 P95 < 250ms
    'redirect_duration': ['p(95)<80'],                  // 리디렉션 P95 < 80ms (캐시 적중)
    'stats_duration': ['p(95)<150'],                    // 통계 P95 < 150ms
  },

  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// Service Endpoints
const URL_SERVICE = __ENV.URL_SERVICE || 'http://localhost:8081';
const REDIRECT_SERVICE = __ENV.REDIRECT_SERVICE || 'http://localhost:8082';
const CLICK_SERVICE = __ENV.CLICK_SERVICE || 'http://localhost:8083';

// Test Data
const POPULAR_SITES = [
  'https://github.com',
  'https://stackoverflow.com',
  'https://reddit.com',
  'https://twitter.com',
  'https://youtube.com',
  'https://linkedin.com',
  'https://medium.com',
  'https://dev.to',
  'https://news.ycombinator.com',
  'https://producthunt.com',
];

let globalShortCodes = [];

function getRandomUrl() {
  return POPULAR_SITES[Math.floor(Math.random() * POPULAR_SITES.length)];
}

/**
 * Setup Phase: 시스템 준비 및 초기 데이터 생성
 */
export function setup() {
  console.log('');
  console.log('╔══════════════════════════════════════════════════════════════╗');
  console.log('║         Phase 1: 1,000 TPS Performance Test - Setup         ║');
  console.log('║                    Target: 100만 DAU                         ║');
  console.log('╚══════════════════════════════════════════════════════════════╝');
  console.log('');

  // Health Check
  console.log('1. Health Check');
  const healthChecks = [
    { name: 'URL Service', url: `${URL_SERVICE}/actuator/health` },
    { name: 'Redirect Service', url: `${REDIRECT_SERVICE}/actuator/health` },
    { name: 'Click Service', url: `${CLICK_SERVICE}/actuator/health` },
  ];

  for (const service of healthChecks) {
    const res = http.get(service.url);
    if (res.status === 200) {
      console.log(`   ✓ ${service.name}: OK`);
    } else {
      throw new Error(`${service.name} is not ready (status: ${res.status})`);
    }
  }

  // Seed Data 생성
  console.log('');
  console.log('2. Seed Data 생성 중...');
  const codes = [];
  const seedCount = 100;

  for (let i = 0; i < seedCount; i++) {
    const url = `${getRandomUrl()}/seed/${i}-${Date.now()}`;
    const payload = JSON.stringify({ originalUrl: url });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const response = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload, params);

    if (response.status === 200 || response.status === 201) {
      try {
        const body = JSON.parse(response.body);
        if (body.shortCode) {
          codes.push(body.shortCode);
        }
      } catch (e) {}
    }

    // 진행 상황 출력
    if (i % 20 === 0 && i > 0) {
      console.log(`   생성 중: ${codes.length}/${seedCount}`);
    }
  }

  if (codes.length < 50) {
    throw new Error(`Setup 실패: ${codes.length}개만 생성됨 (최소 50개 필요)`);
  }

  console.log(`   ✓ Seed Data 준비 완료: ${codes.length}개`);
  console.log('');
  console.log('╔══════════════════════════════════════════════════════════════╗');
  console.log('║                    테스트 시작 (5분)                         ║');
  console.log('║                Target TPS: 1,000 (80/900/20)                 ║');
  console.log('╚══════════════════════════════════════════════════════════════╝');
  console.log('');

  return { shortCodes: codes, startTime: new Date().getTime() };
}

/**
 * Scenario 1: URL 단축 (8% traffic)
 */
export function shortenUrl(data) {
  if (globalShortCodes.length === 0 && data && data.shortCodes) {
    globalShortCodes = data.shortCodes;
  }

  group('URL Shortening', function () {
    const uniqueUrl = `${getRandomUrl()}/test/${__VU}-${__ITER}-${Date.now()}`;
    const payload = JSON.stringify({ originalUrl: uniqueUrl });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const startTime = new Date().getTime();
    const response = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload, params);
    const duration = new Date().getTime() - startTime;

    const success = check(response, {
      'shorten: status is 200/201': (r) => r.status === 200 || r.status === 201,
      'shorten: has shortCode': (r) => {
        try {
          const body = JSON.parse(r.body);
          if (body.shortCode && body.shortCode.length > 0) {
            globalShortCodes.push(body.shortCode);
            // 메모리 관리 (최대 1500개 유지)
            if (globalShortCodes.length > 1500) {
              globalShortCodes = globalShortCodes.slice(-1500);
            }
            return true;
          }
          return false;
        } catch (e) {
          return false;
        }
      },
    });

    shortenSuccessRate.add(success);
    shortenDuration.add(duration);
    totalRequests.add(1);
    if (!success) {
      totalErrors.add(1);
    }
  });
}

/**
 * Scenario 2: 리다이렉션 (90% traffic)
 */
export function redirect(data) {
  if (globalShortCodes.length === 0 && data && data.shortCodes) {
    globalShortCodes = data.shortCodes;
  }

  group('Redirection', function () {
    if (globalShortCodes.length === 0) {
      totalErrors.add(1);
      return;
    }

    const shortCode = globalShortCodes[Math.floor(Math.random() * globalShortCodes.length)];
    const params = { redirects: 0 };  // 리디렉션 따라가지 않음

    const startTime = new Date().getTime();
    const response = http.get(`${REDIRECT_SERVICE}/r/${shortCode}`, params);
    const duration = new Date().getTime() - startTime;

    const success = check(response, {
      'redirect: status is 302': (r) => r.status === 302,
      'redirect: has Location header': (r) => r.headers['Location'] !== undefined,
    });

    redirectSuccessRate.add(success);
    redirectDuration.add(duration);
    totalRequests.add(1);
    if (!success) {
      totalErrors.add(1);
    }
  });
}

/**
 * Scenario 3: 통계 (2% traffic)
 */
export function getStats(data) {
  if (globalShortCodes.length === 0 && data && data.shortCodes) {
    globalShortCodes = data.shortCodes;
  }

  group('Statistics Query', function () {
    if (globalShortCodes.length === 0) {
      totalErrors.add(1);
      return;
    }

    const shortCode = globalShortCodes[Math.floor(Math.random() * globalShortCodes.length)];

    const startTime = new Date().getTime();
    const response = http.get(`${CLICK_SERVICE}/api/v1/analytics/${shortCode}/stats`);
    const duration = new Date().getTime() - startTime;

    const success = check(response, {
      'stats: status is 200': (r) => r.status === 200,
      'stats: has totalClicks': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.totalClicks !== undefined;
        } catch (e) {
          return false;
        }
      },
    });

    statsSuccessRate.add(success);
    statsDuration.add(duration);
    totalRequests.add(1);
    if (!success) {
      totalErrors.add(1);
    }
  });
}

/**
 * 결과 출력
 */
export function teardown(data) {
  if (data && data.startTime) {
    const duration = (new Date().getTime() - data.startTime) / 1000;
    console.log('');
    console.log('╔══════════════════════════════════════════════════════════════╗');
    console.log('║              테스트 완료 - 결과는 아래 Summary 참조           ║');
    console.log('╚══════════════════════════════════════════════════════════════╝');
    console.log(`테스트 소요 시간: ${Math.floor(duration / 60)}분 ${Math.floor(duration % 60)}초`);
    console.log('');
  }
}

/**
 * Summary Handler: 결과 리포트 생성
 */
export function handleSummary(data) {
  const totalReqs = data.metrics.total_requests?.values?.count || 0;
  const totalErrs = data.metrics.total_errors?.values?.count || 0;
  const avgDuration = data.metrics.http_req_duration?.values?.avg || 0;
  const p95Duration = data.metrics.http_req_duration?.values['p(95)'] || 0;
  const p99Duration = data.metrics.http_req_duration?.values['p(99)'] || 0;
  const tps = data.metrics.http_reqs?.values?.rate || 0;

  const shortenSuccess = data.metrics.shorten_success_rate?.values?.rate || 0;
  const redirectSuccess = data.metrics.redirect_success_rate?.values?.rate || 0;
  const statsSuccess = data.metrics.stats_success_rate?.values?.rate || 0;

  const targetTPS = 1000;

  // 순수 테스트 TPS 계산 (setup/teardown 제외)
  const testDuration = 300; // 5분
  const pureTPS = totalReqs / testDuration;
  const tpsAchieved = pureTPS >= targetTPS ? '✅' : '❌';

  console.log('');
  console.log('╔══════════════════════════════════════════════════════════════╗');
  console.log('║            Phase 1: 1,000 TPS Performance Result             ║');
  console.log('╠══════════════════════════════════════════════════════════════╣');
  console.log(`║ Target TPS:            ${String(targetTPS).padStart(10)}                        ║`);
  console.log(`║ k6 Reported TPS:       ${tps.toFixed(2).padStart(10)} (setup 포함)          ║`);
  console.log(`║ Pure Test TPS:         ${pureTPS.toFixed(2).padStart(10)} ${tpsAchieved}                    ║`);
  console.log(`║ Total Requests:        ${String(totalReqs).padStart(10)}                        ║`);
  console.log(`║ Total Errors:          ${String(totalErrs).padStart(10)} (${((totalErrs / totalReqs) * 100).toFixed(2)}%)             ║`);
  console.log('╠══════════════════════════════════════════════════════════════╣');
  console.log(`║ Avg Response Time:     ${avgDuration.toFixed(2).padStart(10)} ms                     ║`);
  console.log(`║ P95 Response Time:     ${p95Duration.toFixed(2).padStart(10)} ms                     ║`);
  console.log(`║ P99 Response Time:     ${p99Duration.toFixed(2).padStart(10)} ms                     ║`);
  console.log('╠══════════════════════════════════════════════════════════════╣');
  console.log(`║ Shorten Success Rate:  ${(shortenSuccess * 100).toFixed(2).padStart(10)}%                      ║`);
  console.log(`║ Redirect Success Rate: ${(redirectSuccess * 100).toFixed(2).padStart(10)}%                      ║`);
  console.log(`║ Stats Success Rate:    ${(statsSuccess * 100).toFixed(2).padStart(10)}%                      ║`);
  console.log('╚══════════════════════════════════════════════════════════════╝');
  console.log('');

  // 파일 출력
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'summary.html': htmlReport(data),
    'phase1-result.json': JSON.stringify({
      phase: 1,
      target: {
        tps: targetTPS,
        dau: 1000000,
      },
      timestamp: new Date().toISOString(),
      summary: {
        totalRequests: totalReqs,
        totalErrors: totalErrs,
        errorRate: ((totalErrs / totalReqs) * 100).toFixed(2),
        k6ReportedTPS: tps.toFixed(2),
        pureTestTPS: pureTPS.toFixed(2),
        tpsAchieved: pureTPS >= targetTPS,
        avgDuration: avgDuration.toFixed(2),
        p95Duration: p95Duration.toFixed(2),
        p99Duration: p99Duration.toFixed(2),
        shortenSuccessRate: (shortenSuccess * 100).toFixed(2),
        redirectSuccessRate: (redirectSuccess * 100).toFixed(2),
        statsSuccessRate: (statsSuccess * 100).toFixed(2),
      },
      fullMetrics: data.metrics,
    }, null, 2),
  };
}
