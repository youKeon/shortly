import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.0.1/index.js';

/**
 * Kafka Producer Performance Benchmark
 * Goal: Stress test the Redirect Service to measure Kafka Producer throughput/latency
 * Target: Maximize Throughput to highlight producer bottlenecks
 */

// Aggressive targets to saturate the producer
const TARGET_TPS = 10000; // Higher TPS to force producer buffering/batching
const TEST_DURATION = '30s'; // Short duration for quick feedback

const redirectSuccessRate = new Rate('redirect_success_rate');
const redirectDuration = new Trend('redirect_duration');
const totalRequests = new Counter('total_requests');
const totalErrors = new Counter('total_errors');

export const options = {
    insecureSkipTLSVerify: true,
    noConnectionReuse: false,

    scenarios: {
        producer_stress: {
            executor: 'constant-arrival-rate',
            rate: TARGET_TPS,
            timeUnit: '1s',
            duration: TEST_DURATION,
            preAllocatedVUs: 1000,
            maxVUs: 3000, // Allow more VUs to handle latency spikes
            exec: 'redirect',
        },
    },

    thresholds: {
        http_req_duration: ['p(95)<200'], // Expect slightly higher latency under stress
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
    console.log('\n===== Kafka Producer Benchmark Setup =====');
    console.log('Checking service health...');
    const healthRes = http.get(`${URL_SERVICE}/actuator/health`);
    if (healthRes.status !== 200) {
        throw new Error(`URL Service is not healthy: ${healthRes.status}`);
    }

    const codes = [];
    const seedCount = 200; // More seeds for higher concurrency

    for (let i = 0; i < seedCount; i++) {
        const payload = JSON.stringify({
            originalUrl: `${POPULAR_SITES[i % POPULAR_SITES.length]}/bench/${i}-${Date.now()}`
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
        }
    }

    if (codes.length < 50) {
        throw new Error(`Failed to generate enough seed URLs. Only ${codes.length}/${seedCount} created.`);
    }

    console.log(`✓ Generated ${codes.length} seed URLs`);
    console.log('Waiting 3s for cache warming...');
    sleep(3);

    return { shortCodes: codes };
}

export function redirect(data) {
    if (globalShortCodes.length === 0 && data?.shortCodes) {
        globalShortCodes = data.shortCodes;
    }

    const shortCode = globalShortCodes[Math.floor(Math.random() * globalShortCodes.length)];

    const start = Date.now();
    const res = http.get(`${REDIRECT_SERVICE}/r/${shortCode}`, {
        redirects: 0,
        timeout: '2s'
    });
    const duration = Date.now() - start;

    const success = check(res, {
        'status is 302': (r) => r.status === 302,
    });

    redirectSuccessRate.add(success);
    redirectDuration.add(duration);
    totalRequests.add(1);
    if (!success) totalErrors.add(1);
}

export function handleSummary(data) {
    return {
        stdout: textSummary(data, { indent: ' ', enableColors: true }),
    };
}
