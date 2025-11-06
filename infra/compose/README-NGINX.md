# Shortly - NGINX Gateway 설정 가이드

## 개요

Shortly 프로젝트에 NGINX를 API Gateway로 추가한 구성입니다.

**왜 NGINX?**
- ✅ 가볍고 빠름 (메모리 10MB, 10만+ TPS 처리)
- ✅ 단순 라우팅 + 로드 밸런싱에 최적
- ✅ Rate Limiting/Auth 불필요 시 완벽한 선택
- ✅ 설정 간단 (30줄)
- ✅ 추가 인프라 불필요

---

## 아키텍처

```
┌─────────────┐
│   Client    │
└──────┬──────┘
       │ HTTP :80
       │
┌──────▼──────────────────┐
│      NGINX              │
│  - Routing              │
│  - Load Balancing       │
│  - Health Check         │
└──────┬──────────────────┘
       │ Internal Network
   ┌───┴───┬─────────┬──────┐
   │       │         │      │
  URL  Redirect   Click  Monitoring
 :8081   :8082    :8083  (Internal)
```

---

## 빠른 시작

### 1. NGINX 포함 전체 시스템 실행

```bash
# Shortly 루트 디렉토리에서
cd /home/user/shortly

# NGINX Gateway와 함께 실행
docker-compose -f infra/compose/docker-compose-with-nginx.yml up -d

# 로그 확인
docker-compose -f infra/compose/docker-compose-with-nginx.yml logs -f nginx
```

### 2. 접속 테스트

```bash
# Health Check
curl http://localhost/health
# Expected: healthy

# URL 생성
curl -X POST http://localhost/api/v1/urls/shorten \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://github.com/youkeon"}'
# Expected: {"shortCode": "abc123", "originalUrl": "..."}

# Redirect 테스트
curl -L http://localhost/r/abc123
# Expected: 302 Redirect

# Click 통계 조회
curl http://localhost/api/v1/clicks/stats/abc123
# Expected: {"shortCode": "abc123", "clickCount": 1}
```

### 3. 서비스 스케일링

```bash
# Redirect Service 스케일 아웃 (95% 트래픽 처리)
docker-compose -f infra/compose/docker-compose-with-nginx.yml up -d --scale redirect-service=3

# NGINX가 자동으로 로드 밸런싱
# nginx.conf의 upstream redirect-service 섹션 참고
```

---

## 엔드포인트

NGINX를 통해 접근 가능한 엔드포인트:

| Path | Target Service | Description |
|------|----------------|-------------|
| `GET /health` | NGINX | Health check |
| `POST /api/v1/urls/shorten` | url-service:8081 | URL 단축 |
| `GET /api/v1/urls/{code}` | url-service:8081 | Short code 조회 |
| `GET /r/{code}` | redirect-service:8082 | 리다이렉트 (95% 트래픽) |
| `GET /api/v1/clicks/stats/{code}` | click-service:8083 | 클릭 통계 |
| `GET /metrics` | NGINX | NGINX 메트릭 |
| `GET /grafana/` | grafana:3000 | 모니터링 대시보드 |
| `GET /prometheus/` | prometheus:9090 | 메트릭 수집 |

---

## 설정 파일

### nginx.conf

주요 설정:

```nginx
# Upstream 정의 (로드 밸런싱 대상)
upstream redirect-service {
    server redirect-service:8082 max_fails=3 fail_timeout=30s;
    # 추가 인스턴스는 자동 검색되지 않음
    # docker-compose scale 사용 시 수동 추가 또는 DNS 기반 설정 사용
}

# 라우팅
location /r/ {
    proxy_pass http://redirect-service;

    # Health Check 연동
    proxy_next_upstream error timeout http_502 http_503 http_504;

    # 성능 최적화
    proxy_buffering off;
}
```

### docker-compose-with-nginx.yml

주요 변경사항:

```yaml
# NGINX Gateway 추가
nginx:
  image: nginx:alpine
  ports:
    - "80:80"  # 단일 진입점
  volumes:
    - ./nginx.conf:/etc/nginx/nginx.conf:ro

# 기존 서비스들의 포트 노출 제거
url-service:
  # ports:  # 제거됨
  #   - "8081:8081"
  expose:
    - "8081"  # 내부 네트워크에서만 접근
```

---

## 성능 최적화

### 1. Worker 프로세스 조정

```nginx
# nginx.conf 상단에 추가
worker_processes auto;  # CPU 코어 수만큼 자동

events {
    worker_connections 4096;  # 동시 접속 수 증가
    use epoll;  # Linux에서 효율적인 이벤트 모델
}
```

### 2. 커넥션 풀링

```nginx
upstream redirect-service {
    server redirect-service:8082;

    # 커넥션 유지
    keepalive 32;
    keepalive_timeout 60s;
}

location /r/ {
    proxy_pass http://redirect-service;
    proxy_http_version 1.1;
    proxy_set_header Connection "";  # Keep-Alive 사용
}
```

### 3. 캐싱 (선택사항)

```nginx
# HTTP 레벨에 추가
proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=shortly_cache:10m max_size=1g inactive=1h;

location /r/ {
    proxy_pass http://redirect-service;

    # 캐싱 설정 (Redirect 서비스가 이미 캐싱하므로 선택적)
    proxy_cache shortly_cache;
    proxy_cache_valid 200 302 1h;
    proxy_cache_key "$request_uri";
}
```

---

## 모니터링

### NGINX 메트릭 확인

```bash
# NGINX Stub Status
curl http://localhost/metrics

# Expected Output:
# Active connections: 125
# server accepts handled requests
#  1234567 1234567 2345678
# Reading: 0 Writing: 3 Waiting: 122
```

### Prometheus 통합

```yaml
# prometheus.yml에 추가
scrape_configs:
  - job_name: 'nginx'
    static_configs:
      - targets: ['nginx:80']
    metrics_path: '/metrics'
```

**더 자세한 메트릭을 위해 nginx-prometheus-exporter 사용 가능:**

```yaml
# docker-compose-with-nginx.yml에 추가
nginx-exporter:
  image: nginx/nginx-prometheus-exporter:latest
  command:
    - '-nginx.scrape-uri=http://nginx/metrics'
  ports:
    - "9113:9113"
  networks:
    - shortly-network
```

---

## 트러블슈팅

### 문제 1: 502 Bad Gateway

```bash
# 원인: 백엔드 서비스가 준비되지 않음
# 해결: Health Check 확인
docker-compose -f infra/compose/docker-compose-with-nginx.yml ps

# 서비스 재시작
docker-compose -f infra/compose/docker-compose-with-nginx.yml restart url-service
```

### 문제 2: 느린 응답 속도

```bash
# NGINX 로그 확인
docker logs shortly-nginx

# 백엔드 서비스 응답 시간 확인
curl -w "@curl-format.txt" -o /dev/null -s http://localhost/api/v1/urls/shorten

# curl-format.txt:
#   time_namelookup:  %{time_namelookup}\n
#   time_connect:  %{time_connect}\n
#   time_appconnect:  %{time_appconnect}\n
#   time_pretransfer:  %{time_pretransfer}\n
#   time_redirect:  %{time_redirect}\n
#   time_starttransfer:  %{time_starttransfer}\n
#   ----------\n
#   time_total:  %{time_total}\n
```

### 문제 3: 로드 밸런싱이 작동하지 않음

```bash
# 현재 upstream 상태 확인 (nginx-plus 필요)
# 대안: 로그로 확인
docker logs shortly-nginx | grep "upstream"

# Redirect Service 인스턴스 확인
docker ps | grep redirect-service

# 수동 스케일링
docker-compose -f infra/compose/docker-compose-with-nginx.yml up -d --scale redirect-service=3
```

---

## 프로덕션 체크리스트

- [ ] SSL/TLS 인증서 설정
- [ ] NGINX 로그 로테이션 설정
- [ ] `worker_processes` 최적화
- [ ] `worker_connections` 증가
- [ ] Access Log 형식 커스터마이징
- [ ] Rate Limiting 추가 (필요 시)
- [ ] GZIP 압축 활성화
- [ ] Security Headers 추가
- [ ] NGINX 모니터링 설정

### SSL/TLS 설정 예시

```nginx
server {
    listen 443 ssl http2;
    server_name shortly.yourdomain.com;

    ssl_certificate /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # ... 기존 location 설정
}

server {
    listen 80;
    server_name shortly.yourdomain.com;
    return 301 https://$server_name$request_uri;
}
```

---

## 다음 단계

### Kubernetes 마이그레이션

NGINX 설정을 Kubernetes로 쉽게 전환할 수 있습니다:

```yaml
# k8s/ingress.yml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: shortly-ingress
spec:
  ingressClassName: nginx
  rules:
  - host: shortly.example.com
    http:
      paths:
      - path: /r
        pathType: Prefix
        backend:
          service:
            name: redirect-service
            port:
              number: 8082
      - path: /api/v1/urls
        pathType: Prefix
        backend:
          service:
            name: url-service
            port:
              number: 8081
```

---

## 성능 벤치마크

### 로컬 테스트 (k6)

```bash
# shortly-test/src/test/k6/ 에서
k6 run --vus 100 --duration 30s smoke-test.js
```

### 예상 성능

| 시나리오 | VUs | RPS | 평균 응답시간 | P95 | P99 |
|---------|-----|-----|--------------|-----|-----|
| Redirect Only | 1000 | 10,000+ | <10ms | <20ms | <50ms |
| Mixed (95% Redirect) | 1000 | 9,500+ | <15ms | <30ms | <100ms |
| URL Creation | 100 | 1,000+ | <50ms | <100ms | <200ms |

---

## 참고 자료

- [NGINX Documentation](https://nginx.org/en/docs/)
- [NGINX Performance Tuning](https://www.nginx.com/blog/tuning-nginx/)
- [Prometheus NGINX Exporter](https://github.com/nginxinc/nginx-prometheus-exporter)

---

## 문의

Shortly 프로젝트 관련 문의: [GitHub Issues](https://github.com/youkeon/shortly/issues)
