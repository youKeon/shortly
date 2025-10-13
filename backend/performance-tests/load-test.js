import http from 'k6/http';
import { check } from 'k6';
export const options = {
  vus: 2000, duration: '60s', thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<50']
  }
};
export default function () {
  const res = http.get('http://localhost:8080/api/v1/urls', { redirects: 0 });
  check(res, { '302': r => r.status === 302 });
}
