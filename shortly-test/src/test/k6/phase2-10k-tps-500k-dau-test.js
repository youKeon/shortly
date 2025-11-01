import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

/**
 * Phase 2: High Performance Validation Test
 *
 * 실행 방법:
 * - MVC 버전 (default): k6 run phase2-10k-tps-500k-dau-test.js
 * - WebFlux 버전: k6 run -e PROFILE=webflux phase2-10k-tps-500k-dau-test.js
 *
 * 목표: 10,000 TPS, 500만 DAU
 * 시간: 8분
 * 부하: 점진적 증가 → 최대 부하 유지 → 감소
 *
 * 검증 지표:
 * - TPS: 10,000 이상
 * - P95 응답 시간: < 500ms
 * - P99 응답 시간: < 1000ms
 * - 에러율: < 5%
 * - 성공률: > 95%
 */

// Custom Metrics
const shortenSuccessRate = new Rate('shorten_success_rate');
const redirectSuccessRate = new Rate('redirect_success_rate');
const statsSuccessRate = new Rate('stats_success_rate');

const shortenDuration = new Trend('shorten_duration');
const redirectDuration = new Trend('redirect_duration');
const statsDuration = new Trend('stats_duration');

const totalRequests = new Counter('total_requests');
const totalErrors = new Counter('total_errors');
const currentTPS = new Gauge('current_tps');

// Test Configuration
export const options = {
  scenarios: {
    // Scenario 1: URL Creation (8% of traffic, Target: 800 TPS)
    url_creation: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 200 },    // Warm-up: 25% target
        { duration: '2m', target: 400 },    // Ramp-up: 50% target
        { duration: '3m', target: 800 },    // Peak: 100% target ⭐
        { duration: '2m', target: 0 },      // Cool-down
      ],
      exec: 'shortenUrl',
      gracefulRampDown: '30s',
    },

    // Scenario 2: Redirection (90% of traffic, Target: 9,000 TPS)
    redirection: {
      executor: 'ramping-vus',
      startVUs: 0,
      startTime: '10s',  // 시작 지연 (URL 생성 후)
      stages: [
        { duration: '50s', target: 1125 },  // Warm-up: 25% target
        { duration: '2m', target: 2250 },   // Ramp-up: 50% target
        { duration: '3m', target: 4500 },   // Peak: 100% target ⭐
        { duration: '2m', target: 0 },      // Cool-down
      ],
      exec: 'redirect',
      gracefulRampDown: '30s',
    },

    // Scenario 3: Statistics Query (2% of traffic, Target: 200 TPS)
    statistics: {
      executor: 'ramping-vus',
      startVUs: 0,
      startTime: '20s',  // 시작 지연 (충분한 데이터 생성 후)
      stages: [
        { duration: '40s', target: 90 },    // Warm-up: 25% target
        { duration: '2m', target: 175 },    // Ramp-up: 50% target
        { duration: '3m', target: 350 },    // Peak: 100% target ⭐
        { duration: '2m', target: 0 },      // Cool-down
      ],
      exec: 'getStats',
      gracefulRampDown: '30s',
    },
  },

  thresholds: {
    // 성능 임계값 (Phase 2: 목표 TPS 달성)
    'http_req_duration': ['p(95)<500', 'p(99)<1000'],   // P95 < 500ms, P99 < 1s
    'http_req_failed': ['rate<0.05'],                   // 에러율 < 5%

    // 기능별 성공률
    'shorten_success_rate': ['rate>0.95'],              // 단축 성공률 > 95%
    'redirect_success_rate': ['rate>0.95'],             // 리디렉션 성공률 > 95%
    'stats_success_rate': ['rate>0.90'],                // 통계 조회 성공률 > 90%

    // 기능별 응답 시간
    'shorten_duration': ['p(95)<300'],                  // 단축 P95 < 300ms
    'redirect_duration': ['p(95)<100'],                 // 리디렉션 P95 < 100ms (캐시 적중)
    'stats_duration': ['p(95)<200'],                    // 통계 P95 < 200ms
  },

  // Summary 설정
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

// Profile 설정 (default: mvc)
const PROFILE = __ENV.PROFILE || 'mvc';
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
  console.log('║        Phase 2: 10,000 TPS Performance Test - Setup         ║');
  console.log(`║                    Profile: ${PROFILE.toUpperCase().padEnd(8)}                          ║`);
  console.log('║                    Target: 500만 DAU                         ║');
  console.log('╚══════════════════════════════════════════════════════════════╝');
  console.log('');

  // Health Check
  console.log('1. Health Check 중...');
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
  const seedCount = 300;  // Phase 2는 더 많은 Seed Data

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
      } catch (e) {
        // JSON 파싱 실패 무시
      }
    }

    // 부하 분산
    if (i % 30 === 0 && i > 0) {
      console.log(`   생성 중: ${codes.length}/${seedCount}`);
      sleep(0.5);
    }
  }

  if (codes.length < 150) {
    throw new Error(`Setup 실패: ${codes.length}개만 생성됨 (최소 150개 필요)`);
  }

  console.log(`   ✓ Seed Data 준비 완료: ${codes.length}개`);
  console.log('');
  console.log('╔══════════════════════════════════════════════════════════════╗');
  console.log('║                    테스트 시작 (8분)                         ║');
  console.log('║               Target TPS: 10,000 (800/9,000/200)             ║');
  console.log('╚══════════════════════════════════════════════════════════════╝');
  console.log('');

  return { shortCodes: codes, startTime: new Date().getTime() };
}

/**
 * Scenario 1: URL Shortening (8% traffic)
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
            // 메모리 관리 (최대 5000개 유지)
            if (globalShortCodes.length > 5000) {
              globalShortCodes = globalShortCodes.slice(-5000);
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

    sleep(Math.random() * 2 + 1);  // 1-3초 대기
  });
}

/**
 * Scenario 2: Redirection (90% traffic)
 */
export function redirect(data) {
  if (globalShortCodes.length === 0 && data && data.shortCodes) {
    globalShortCodes = data.shortCodes;
  }

  group('Redirection', function () {
    if (globalShortCodes.length === 0) {
      totalErrors.add(1);
      sleep(5);
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

    sleep(Math.random() * 0.5);  // 0-0.5초 대기
  });
}

/**
 * Scenario 3: Statistics Query (2% traffic)
 */
export function getStats(data) {
  if (globalShortCodes.length === 0 && data && data.shortCodes) {
    globalShortCodes = data.shortCodes;
  }

  group('Statistics Query', function () {
    if (globalShortCodes.length === 0) {
      totalErrors.add(1);
      sleep(5);
      return;
    }

    const shortCode = globalShortCodes[Math.floor(Math.random() * globalShortCodes.length)];

    const startTime = new Date().getTime();
    const response = http.get(`${CLICK_SERVICE}/api/v1/clicks/${shortCode}/stats`);
    const duration = new Date().getTime() - startTime;

    const success = check(response, {
      'stats: status is 200': (r) => r.status === 200,
      'stats: has clickCount': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.clickCount !== undefined;
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

    sleep(Math.random() * 3 + 2);  // 2-5초 대기
  });
}

/**
 * Teardown Phase: 결과 출력
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

  const targetTPS = 10000;
  const tpsAchieved = tps >= targetTPS ? '✅' : '❌';

  console.log('');
  console.log('╔══════════════════════════════════════════════════════════════╗');
  console.log('║           Phase 2: 10,000 TPS Performance Result             ║');
  console.log(`║                    Profile: ${PROFILE.toUpperCase()}                              ║`);
  console.log('╠══════════════════════════════════════════════════════════════╣');
  console.log(`║ Target TPS:            ${String(targetTPS).padStart(10)}                        ║`);
  console.log(`║ Actual TPS:            ${tps.toFixed(2).padStart(10)} ${tpsAchieved}                    ║`);
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
    'phase2-result.json': JSON.stringify({
      phase: 2,
      profile: PROFILE,
      target: {
        tps: targetTPS,
        dau: 5000000,
      },
      timestamp: new Date().toISOString(),
      summary: {
        totalRequests: totalReqs,
        totalErrors: totalErrs,
        errorRate: ((totalErrs / totalReqs) * 100).toFixed(2),
        tps: tps.toFixed(2),
        tpsAchieved: tps >= targetTPS,
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
