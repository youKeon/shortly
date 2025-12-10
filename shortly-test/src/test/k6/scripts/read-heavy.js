import http from "k6/http";
import { check, sleep } from "k6";
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

const REDIRECT_BASE_URL = "http://192.168.219.101:8082";
const URL_BASE_URL = __ENV.URL_HOST || "http://192.168.219.101:8081";

export const options = {
  stages: [
    { duration: "10s", target: 30 },
    { duration: "20s", target: 1000 },
    { duration: "30s", target: 1200 },
    { duration: "10s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<50"],
  },
  setupTimeout: '120s',
};

export function setup() {
  const codes = [];
  const reqs = [];
  const COUNT = 10;

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

  responses.forEach((res) => {
    if (res.status === 200 || res.status === 201) {
      try {
        const body = JSON.parse(res.body);
        if (body.shortCode) {
          codes.push(body.shortCode);
        }
      } catch (e) {
        console.error("Failed to parse JSON:", e);
      }
    } else {
      console.error(`Failed to shorten URL. Status: ${res.status}`);
    }
  });

  sleep(2); // pub/sub 처리 대기

  codes.forEach(code => {
    http.get(`${REDIRECT_BASE_URL}/r/${code}`, { redirects: 0 });
  });

  return { codes };
}

export default function (data) {
  const codes = data.codes;
  if (!codes || codes.length === 0) {
    console.error("No codes available for testing");
    return;
  }

  const code = codes[Math.floor(Math.random() * codes.length)];
  const res = http.get(`${REDIRECT_BASE_URL}/r/${code}`, { redirects: 0 });

  check(res, {
    "status is 302": (r) => r.status === 302,
  });
}
