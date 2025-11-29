import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

/**
 * Test 1: High-Load Redirection Test (Read-Heavy)
 * Goal: Validate Read Performance (Cache Hit/Miss, DB Replica)
 * Target: 5,000 TPS
 */
const TARGET_TPS = 5000;
const TEST_DURATION = '5m';

const redirectSuccessRate = new Rate('redirect_success_rate');
const redirectDuration = new Trend('redirect_duration');
const totalRequests = new Counter('total_requests');
const totalErrors = new Counter('total_errors');

export const options = {
    insecureSkipTLSVerify: true,
    noConnectionReuse: false,

    scenarios: {
        redirection_storm: {
            executor: 'constant-arrival-rate',
            rate: TARGET_TPS,
            timeUnit: '1s',
            duration: TEST_DURATION,
            preAllocatedVUs: 500,
            maxVUs: 2000,
            exec: 'redirect',
        },
    },

    thresholds: {
        http_req_duration: ['p(95)<100', 'p(99)<500'], // Fast reads expected
        redirect_success_rate: ['rate>0.99'],
    },
};

const TARGET_HOST = __ENV.TARGET_HOST || 'http://localhost';
const URL_SERVICE = __ENV.URL_SERVICE || `${TARGET_HOST}:8081`;
const REDIRECT_SERVICE = __ENV.REDIRECT_SERVICE || `${TARGET_HOST}:8082`;

const POPULAR_SITES = [
    'https://github.com', 'https://stackoverflow.com', 'https://google.com',
    'https://youtube.com', 'https://netflix.com', 'https://amazon.com'
];

let globalShortCodes = [];

export function setup() {
    console.log('\n===== Test 1: Redirection High-Load Setup =====');
    console.log('Checking service health...');
    const healthRes = http.get(`${URL_SERVICE}/actuator/health`);
    if (healthRes.status !== 200) {
        throw new Error(`URL Service is not healthy: ${healthRes.status}`);
    }

    const codes = [];
    const seedCount = 100;  // 100개 URL 생성

    // 100개 URL을 순차적으로 생성
    for (let i = 0; i < seedCount; i++) {
        const payload = JSON.stringify({
            originalUrl: `${POPULAR_SITES[i % POPULAR_SITES.length]}/seed/${i}-${Date.now()}`
        });
        const params = {
            headers: { 'Content-Type': 'application/json' },
            timeout: '5s'
        };
        const res = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload, params);

        if (res.status === 200 || res.status === 201) {
            try {
                const body = JSON.parse(res.body);
                if (body.shortCode) {
                    codes.push(body.shortCode);
                }
            } catch (e) {
                console.warn(`Failed to parse response: ${e.message}`);
            }
        } else {
            console.warn(`Failed to create URL #${i + 1}: status=${res.status}`);
        }

        if ((i + 1) % 20 === 0) {
            console.log(`  Progress: ${i + 1}/${seedCount} URLs created`);
        }
    }

    if (codes.length < 50) {
        throw new Error(`Failed to generate enough seed URLs. Only ${codes.length}/${seedCount} created.`);
    }

    console.log(`✓ Successfully generated ${codes.length} seed URLs`);

    // Cache warming을 위해 5초 대기 (Kafka 이벤트 처리)
    console.log('Waiting 5s for cache warming via Kafka events...');
    sleep(5);

    console.log('===== Setup Complete. Starting high-load redirection test... =====\n');

    return { shortCodes: codes };
}

export function redirect(data) {
    if (globalShortCodes.length === 0 && data?.shortCodes) {
        globalShortCodes = data.shortCodes;
    }

    const shortCode = globalShortCodes[Math.floor(Math.random() * globalShortCodes.length)];

    const start = Date.now();
    const res = http.get(`${REDIRECT_SERVICE}/r/${shortCode}`, {
        redirects: 0, // We want to check the 302 response itself
        timeout: '3s'
    });
    const duration = Date.now() - start;

    const success = check(res, {
        'status is 302': (r) => r.status === 302,
        'has location header': (r) => r.headers['Location'] !== undefined,
    });

    redirectSuccessRate.add(success);
    redirectDuration.add(duration);
    totalRequests.add(1);
    if (!success) totalErrors.add(1);
}

export function handleSummary(data) {
    return {
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
        'report-redirect.html': htmlReport(data),
    };
}
