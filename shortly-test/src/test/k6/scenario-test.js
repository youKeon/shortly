import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { htmlReport } from 'https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bundle.js';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

/**
 * Test 3: Real User Flow Scenario (Consistency Test)
 * Goal: Validate Data Consistency (Replication Lag, Eventual Consistency)
 * Flow: Shorten -> Wait 3s -> Redirect
 * Target: 100 TPS (Focus on correctness, not just raw throughput)
 */
const TARGET_TPS = 100;
const TEST_DURATION = '5m';
const WAIT_TIME_SEC = 3;

const flowSuccessRate = new Rate('flow_success_rate');
const flowDuration = new Trend('flow_duration');
const totalRequests = new Counter('total_requests');
const totalErrors = new Counter('total_errors');

export const options = {
    insecureSkipTLSVerify: true,
    noConnectionReuse: false,

    scenarios: {
        user_journey: {
            executor: 'constant-arrival-rate',
            rate: TARGET_TPS,
            timeUnit: '1s',
            duration: TEST_DURATION,
            // Each VU takes at least 3s (wait time). To sustain 100 TPS, we need 100 * 3 = 300 active VUs.
            // Setting maxVUs to 500 to provide headroom.
            preAllocatedVUs: 300,
            maxVUs: 500,
            exec: 'userFlow',
        },
    },

    thresholds: {
        http_req_duration: ['p(95)<2000'], // Includes wait time? No, http_req_duration is only request time.
        flow_success_rate: ['rate>0.99'], // 99% consistency required
    },
};

const TARGET_HOST = __ENV.TARGET_HOST || 'http://localhost';
const URL_SERVICE = __ENV.URL_SERVICE || `${TARGET_HOST}:8081`;
const REDIRECT_SERVICE = __ENV.REDIRECT_SERVICE || `${TARGET_HOST}:8082`;

const POPULAR_SITES = [
    'https://github.com', 'https://stackoverflow.com', 'https://google.com',
    'https://youtube.com', 'https://netflix.com', 'https://amazon.com'
];

export function userFlow() {
    const originalUrl = `${POPULAR_SITES[Math.floor(Math.random() * POPULAR_SITES.length)]}/flow/${__VU}-${__ITER}-${Date.now()}`;
    const payload = JSON.stringify({ originalUrl: originalUrl });
    const params = { headers: { 'Content-Type': 'application/json' } };

    // Step 1: Shorten
    const start = Date.now();
    const shortenRes = http.post(`${URL_SERVICE}/api/v1/urls/shorten`, payload, params);

    const shortenSuccess = check(shortenRes, {
        'shorten status 200/201': (r) => r.status === 200 || r.status === 201,
        'shorten has shortCode': (r) => {
            try {
                return JSON.parse(r.body).shortCode !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    if (!shortenSuccess) {
        flowSuccessRate.add(false);
        totalErrors.add(1);
        return; // Stop if shortening failed
    }

    const shortCode = JSON.parse(shortenRes.body).shortCode;

    // Step 2: Wait (Simulate user sharing the link)
    sleep(WAIT_TIME_SEC);

    // Step 3: Redirect
    const redirectRes = http.get(`${REDIRECT_SERVICE}/r/${shortCode}`, {
        redirects: 0,
        timeout: '5s'
    });

    const redirectSuccess = check(redirectRes, {
        'redirect status 302': (r) => r.status === 302,
        'redirect location matches': (r) => r.headers['Location'] === originalUrl,
    });

    const duration = Date.now() - start;

    flowSuccessRate.add(redirectSuccess);
    flowDuration.add(duration);
    totalRequests.add(1); // Count 1 flow as 1 request for simplicity in this metric
    if (!redirectSuccess) totalErrors.add(1);
}

export function handleSummary(data) {
    return {
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
        'report-flow.html': htmlReport(data),
    };
}
