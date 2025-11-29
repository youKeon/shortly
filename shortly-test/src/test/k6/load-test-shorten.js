import http from 'k6/http';
import { check } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

/**
 * Test 2: High-Load URL Shortening Test (Write-Heavy)
 * Goal: Validate Write Performance (Snowflake ID, DB Insert, Kafka Produce)
 * Target: 1,000 TPS
 */
const TARGET_TPS = 1000;
const TEST_DURATION = '5m';

const shortenSuccessRate = new Rate('shorten_success_rate');
const shortenDuration = new Trend('shorten_duration');
const totalRequests = new Counter('total_requests');
const totalErrors = new Counter('total_errors');

export const options = {
    insecureSkipTLSVerify: true,
    noConnectionReuse: false,

    scenarios: {
        shorten_storm: {
            executor: 'constant-arrival-rate',
            rate: TARGET_TPS,
            timeUnit: '1s',
            duration: TEST_DURATION,
            preAllocatedVUs: 200,
            maxVUs: 1000,
            exec: 'shorten',
        },
    },

    thresholds: {
        http_req_duration: ['p(95)<300', 'p(99)<800'], // Writes are slower than reads
        shorten_success_rate: ['rate>0.99'],
    },
};

const TARGET_HOST = __ENV.TARGET_HOST || 'http://localhost';
const URL_SERVICE = __ENV.URL_SERVICE || `${TARGET_HOST}:8081`;

const POPULAR_SITES = [
    'https://github.com', 'https://stackoverflow.com', 'https://google.com',
    'https://youtube.com', 'https://netflix.com', 'https://amazon.com'
];

export function shorten() {
    const url = `${POPULAR_SITES[Math.floor(Math.random() * POPULAR_SITES.length)]}/perf/${__VU}-${__ITER}-${Date.now()}`;
    const payload = JSON.stringify({ originalUrl: url });
    const params = { headers: { 'Content-Type': 'application/json' } };

    const start = Date.now();
    const res = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload, params);
    const duration = Date.now() - start;

    const success = check(res, {
        'status is 200 or 201': (r) => r.status === 200 || r.status === 201,
        'has shortCode': (r) => {
            try {
                return JSON.parse(r.body).shortCode !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    shortenSuccessRate.add(success);
    shortenDuration.add(duration);
    totalRequests.add(1);
    if (!success) totalErrors.add(1);
}

export function handleSummary(data) {
    return {
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
        'report-shorten.html': htmlReport(data),
    };
}
