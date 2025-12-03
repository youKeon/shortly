import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
    vus: 5,
    duration: '30s',
    thresholds: {
        http_req_failed: ['rate<0.01'], // http errors should be less than 1%
        http_req_duration: ['p(95)<500'], // 95% of requests should be below 500ms
    },
};

const BASE_URL = 'http://host.docker.internal:8081'; // URL Service via Gateway/LB if exists, or direct
const REDIRECT_URL = 'http://host.docker.internal:8082'; // Redirect Service

export default function () {
    // 1. Shorten URL
    const originalUrl = `https://example.com/${randomString(10)}`;
    const payload = JSON.stringify({
        originalUrl: originalUrl,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const shortenRes = http.post(`${BASE_URL}/api/v1/urls/shorten`, payload, params);

    check(shortenRes, {
        'shorten status is 201': (r) => r.status === 201,
        'has shortCode': (r) => r.json('shortCode') !== undefined,
    });

    if (shortenRes.status === 201) {
        const shortCode = shortenRes.json('shortCode');

        // 2. Redirect (First access - likely Cache Miss & DB Access & Cache Put)
        const redirectRes1 = http.get(`${REDIRECT_URL}/r/${shortCode}`, {
            redirects: 0 // 리다이렉트를 자동으로 따라가지 않음 (302 확인 위해)
        });

        check(redirectRes1, {
            'redirect 1 status is 302': (r) => r.status === 302,
            'redirect 1 location correct': (r) => r.headers['Location'] === originalUrl,
        });

        sleep(1);

        // 3. Redirect (Second access - likely Cache Hit)
        const redirectRes2 = http.get(`${REDIRECT_URL}/r/${shortCode}`, {
            redirects: 0
        });

        check(redirectRes2, {
            'redirect 2 status is 302': (r) => r.status === 302,
            'redirect 2 location correct': (r) => r.headers['Location'] === originalUrl,
        });
    }

    sleep(1);
}
