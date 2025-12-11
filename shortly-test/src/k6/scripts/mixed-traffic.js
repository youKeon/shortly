import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// Configuration
const BASE_URL_URL_SERVICE = __ENV.URL_HOST || 'http://localhost:8081';
const BASE_URL_REDIRECT_SERVICE = __ENV.REDIRECT_HOST || 'http://localhost:8082';
const HEADERS = { 'Content-Type': 'application/json' };

export const options = {
    stages: [
        { duration: '30s', target: 50 }, // Ramp up
        { duration: '1m', target: 50 },  // Stay
        { duration: '30s', target: 0 },  // Ramp down
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        http_req_duration: ['p(95)<200'],
    },
};

function generatePayload() {
    return JSON.stringify({
        originalUrl: `https://example.com/${randomString(10)}`
    });
}

export function setup() {
    const shortCodes = [];
    for (let i = 0; i < 50; i++) {
        const payload = generatePayload();
        const res = http.post(`${BASE_URL_URL_SERVICE}/api/v1/urls/shorten`, payload, { headers: HEADERS });
        if (res.status === 201) {
            shortCodes.push(JSON.parse(res.body).shortCode);
        }
    }
    return { shortCodes };
}

export default function (data) {
    const rand = Math.random();

    if (rand < 0.1) {
        // Write
        const payload = generatePayload();
        const res = http.post(`${BASE_URL_URL_SERVICE}/api/v1/urls/shorten`, payload, { headers: HEADERS });
        check(res, { 'write status is 201': (r) => r.status === 201 });
    } else {
        // Read
        const shortCode = data.shortCodes[Math.floor(Math.random() * data.shortCodes.length)];
        const res = http.get(`${BASE_URL_REDIRECT_SERVICE}/r/${shortCode}`, { redirects: 0 });
        check(res, { 'read status is 302': (r) => r.status === 302 });
    }

    sleep(1);
}
