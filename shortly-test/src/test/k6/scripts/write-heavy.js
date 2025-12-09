import http from 'k6/http';
import { check } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

const BASE_URL = __ENV.REDIRECT_HOST || "http://localhost:8082";

export const options = {
  stages: [
    { duration: '10s', target: 100 },
    { duration: '30s', target: 300 },
    { duration: '20s', target: 500 },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
  },
};

function generatePayload() {
  return JSON.stringify({
    originalUrl: `https://example.com/${randomString(16)}`
  });
}

export default function () {
  const payload = generatePayload();

  const res = http.post(`${BASE_URL}/api/v1/urls/shorten`, payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  check(res, {
    'status 201': (r) => r.status === 201,
    'shortCode exists': (r) => {
      try {
        return JSON.parse(r.body).shortCode !== undefined;
      } catch (_) {
        return false;
      }
    }
  });
}
