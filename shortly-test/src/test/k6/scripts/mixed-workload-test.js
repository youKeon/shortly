import http from "k6/http";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import { Counter, Trend } from "k6/metrics";

const REDIRECT_BASE_URL = __ENV.REDIRECT_HOST || "http://192.168.219.101:8082";
const URL_BASE_URL = __ENV.URL_HOST || "http://192.168.219.101:8081";

// 커스텀 메트릭
const urlCreatedCounter = new Counter("url_created_total");
const redirectCounter = new Counter("redirect_total");
const cacheHitTrend = new Trend("cache_hit_latency");
const cacheMissTrend = new Trend("cache_miss_latency");

// 공유 데이터: 생성된 shortCode 저장
const codes = [];

export const options = {
  scenarios: {
    // 시나리오 1: URL 생성 (지속적)
    url_creator: {
      executor: "constant-arrival-rate",
      rate: 50,              // 초당 50개 URL 생성
      timeUnit: "1s",
      duration: "30m",
      preAllocatedVUs: 10,
      maxVUs: 50,
      exec: "createUrl",
    },
    // 시나리오 2: 리다이렉트 요청 (고부하)
    redirect_load: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "1m", target: 200 },   // 워밍업
        { duration: "5m", target: 800 },   // 부하 증가
        { duration: "18m", target: 1000 }, // 최대 부하 유지
        { duration: "5m", target: 800 },   // 부하 감소
        { duration: "1m", target: 0 },     // 쿨다운
      ],
      exec: "redirectRequest",
      startTime: "30s",  // URL 생성 30초 후 시작
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{scenario:redirect_load}": ["p(95)<100"],
    "http_req_duration{scenario:url_creator}": ["p(95)<500"],
  },
};

// 초기 URL 생성 (테스트 시작 전)
export function setup() {
  console.log("Creating initial URLs...");
  
  const initialCodes = [];
  const batchSize = 100;
  const initialCount = 1000;
  
  for (let batch = 0; batch < initialCount / batchSize; batch++) {
    const reqs = [];
    
    for (let i = 0; i < batchSize; i++) {
      const payload = JSON.stringify({
        originalUrl: `https://example.com/initial-${randomString(8)}-${batch * batchSize + i}`
      });
      
      reqs.push({
        method: 'POST',
        url: `${URL_BASE_URL}/api/v1/urls/shorten`,
        body: payload,
        params: { headers: { 'Content-Type': 'application/json' } }
      });
    }
    
    const responses = http.batch(reqs);
    responses.forEach((res) => {
      if (res.status === 200 || res.status === 201) {
        try {
          const body = JSON.parse(res.body);
          if (body.shortCode) {
            initialCodes.push(body.shortCode);
          }
        } catch (e) {}
      }
    });
  }
  
  console.log(`Initial setup complete. Created ${initialCodes.length} URLs.`);
  sleep(3); // pub/sub 처리 대기
  
  return { initialCodes };
}

// 시나리오 1: URL 생성
export function createUrl() {
  const payload = JSON.stringify({
    originalUrl: `https://example.com/dynamic-${randomString(10)}-${Date.now()}`
  });
  
  const res = http.post(
    `${URL_BASE_URL}/api/v1/urls/shorten`,
    payload,
    { headers: { 'Content-Type': 'application/json' } }
  );
  
  const success = check(res, {
    "url created": (r) => r.status === 200 || r.status === 201,
  });
  
  if (success) {
    try {
      const body = JSON.parse(res.body);
      if (body.shortCode) {
        codes.push(body.shortCode);
        urlCreatedCounter.add(1);
      }
    } catch (e) {}
  }
}

// 시나리오 2: 리다이렉트 요청
export function redirectRequest(data) {
  // 초기 생성된 URL + 동적 생성된 URL 모두 사용
  const allCodes = [...data.initialCodes, ...codes];
  
  if (allCodes.length === 0) {
    sleep(0.1);
    return;
  }
  
  // Zipf 분포: 인기 URL에 트래픽 집중
  const index = getZipfRandomIndex(allCodes.length);
  const code = allCodes[index];
  
  const startTime = Date.now();
  const res = http.get(`${REDIRECT_BASE_URL}/r/${code}`, { redirects: 0 });
  const duration = Date.now() - startTime;
  
  check(res, {
    "redirect success": (r) => r.status === 302,
  });
  
  redirectCounter.add(1);
  
  // 응답 시간으로 캐시 히트/미스 추정 (10ms 기준)
  if (duration < 10) {
    cacheHitTrend.add(duration);
  } else {
    cacheMissTrend.add(duration);
  }
}

// Zipf 분포: 80/20 법칙
function getZipfRandomIndex(length) {
  const r = Math.random();
  if (r < 0.8) {
    // 80% 트래픽 → 상위 20% URL
    return Math.floor(Math.random() * Math.max(1, length * 0.2));
  }
  return Math.floor(Math.random() * length);
}

export function teardown(data) {
  console.log(`\n=== Test Summary ===`);
  console.log(`Initial URLs: ${data.initialCodes.length}`);
  console.log(`Dynamic URLs created: ${codes.length}`);
  console.log(`Total URLs: ${data.initialCodes.length + codes.length}`);
}

