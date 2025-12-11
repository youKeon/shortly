import http from "k6/http";
import { check, sleep } from "k6";
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

const REDIRECT_BASE_URL = __ENV.REDIRECT_HOST || "http://localhost:8082";
const URL_BASE_URL = __ENV.URL_HOST || "http://localhost:8081";

export const options = {
    stages: [
        // 워밍업 (1분)
        { duration: "30s", target: 100 },
        { duration: "30s", target: 500 },

        // 고부하 단계 1 (10분)
        { duration: "1m", target: 1000 },
        { duration: "9m", target: 1000 },

        // 고부하 단계 2 (10분)
        { duration: "1m", target: 1500 },
        { duration: "9m", target: 1500 },

        // 최대 부하 (5분)
        { duration: "1m", target: 2000 },
        { duration: "4m", target: 2000 },

        // 안정화 (4분)
        { duration: "2m", target: 1000 },
        { duration: "2m", target: 500 },

        // 쿨다운
        { duration: "1m", target: 0 },
    ],
    thresholds: {
        http_req_failed: ["rate<0.01"],
        http_req_duration: ["p(95)<100", "p(99)<200"],
    },
    setupTimeout: '120s',
};

export function setup() {
    const codes = [];
    const reqs = [];
    const COUNT = 50; // 더 많은 URL 생성으로 캐시 히트율 테스트

    console.log(`Setting up ${COUNT} short URLs...`);

    for (let i = 0; i < COUNT; i++) {
        const payload = JSON.stringify({
            originalUrl: `https://example.com/page-${randomString(10)}-${i}`
        });

        reqs.push({
            method: 'POST',
            url: `${URL_BASE_URL}/api/v1/urls/shorten`,
            body: payload,
            params: {
                headers: { 'Content-Type': 'application/json' }
            }
        });
    }

    const responses = http.batch(reqs);

    responses.forEach((res, index) => {
        if (res.status === 200 || res.status === 201) {
            try {
                const body = JSON.parse(res.body);
                if (body.shortCode) {
                    codes.push(body.shortCode);
                }
            } catch (e) {
                console.error(`Failed to parse JSON at index ${index}:`, e);
            }
        } else {
            console.error(`Failed to shorten URL at index ${index}. Status: ${res.status}`);
        }
    });

    console.log(`Successfully created ${codes.length} short URLs`);

    // pub/sub 처리 및 캐시 워밍 대기
    sleep(3);

    // 초기 캐시 워밍
    codes.forEach(code => {
        http.get(`${REDIRECT_BASE_URL}/r/${code}`, { redirects: 0 });
    });

    console.log("Setup completed - cache warmed up");

    return { codes };
}

export default function (data) {
    const codes = data.codes;
    if (!codes || codes.length === 0) {
        console.error("No codes available for testing");
        return;
    }

    // 파레토 법칙 시뮬레이션: 20%의 URL이 80%의 트래픽 차지
    const rand = Math.random();
    let code;

    if (rand < 0.8) {
        // 80% 확률로 상위 20% URL 선택 (핫 데이터)
        const hotDataSize = Math.floor(codes.length * 0.2);
        const hotIndex = Math.floor(Math.random() * hotDataSize);
        code = codes[hotIndex];
    } else {
        // 20% 확률로 나머지 80% URL 선택 (롱테일)
        const coldDataStart = Math.floor(codes.length * 0.2);
        const coldIndex = coldDataStart + Math.floor(Math.random() * (codes.length - coldDataStart));
        code = codes[coldIndex];
    }

    const res = http.get(`${REDIRECT_BASE_URL}/r/${code}`, { redirects: 0 });

    check(res, {
        "status is 302": (r) => r.status === 302,
    });

    // 실제 사용자 행동 시뮬레이션 (think time)
    sleep(Math.random() * 0.5); // 0~500ms 랜덤 대기
}

export function teardown(data) {
    console.log(`Test completed with ${data.codes.length} short URLs`);
}
