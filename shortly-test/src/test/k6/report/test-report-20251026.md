# 스트레스 테스트 결과 보고서
- 테스트 일시: 2025-10-26 01:09:39 ~ 01:28:51 (약 19분)
- 테스트 스크립트: stress-test.js

---

## 1. 테스트 개요

### 1.1 테스트 목적
- 시스템의 최대 처리 용량 측정
- 병목 지점 식별
- 장애 발생 시점 및 원인 파악

### 1.2 테스트 시나리오
- 점진적 부하 증가 방식 (6단계)
- 트래픽 비율: URL 생성 8%, 리다이렉트 90%, 통계 조회 2%

| Stage | URL Creation VU | Redirection VU | Statistics VU | Total VU |
|-------|-----------------|----------------|---------------|----------|
| 1 (Warmup) | 20 | 500 | 10 | 530 |
| 2 (Low) | 50 | 1,000 | 20 | 1,070 |
| 3 (Medium) | 100 | 2,000 | 40 | 2,140 |
| 4 (High) | 200 | 3,000 | 60 | 3,260 |
| 5 (Very High) | 300 | 4,000 | 80 | 4,380 |
| 6 (Maximum) | 400 | 5,000 | 100 | 5,500 |



---

## 2. 테스트 환경

### 2.1 시스템 구성
- **서비스 실행**: 로컬 (gradlew bootRun)
- **인프라**: Docker Compose (MySQL, Redis, Kafka, Prometheus, Grafana, Loki)
- **OS**: macOS Darwin 23.5.0

### 2.2 서비스 구성

**URL Service**
- Database: MySQL
- Connection Pool: HikariCP (기본 설정)

**Redirect Service**
- Database: MySQL R2DBC
- Cache: L1 (Caffeine) + L2 (Redis)
- Server: Spring WebFlux

**Click Service**
- Database: MySQL
- Connection Pool: HikariCP (기본 설정)

### 2.3 초기 설정값

**HikariCP (URL/Click Service)**
- 기본값 사용

**R2DBC Pool (Redirect Service)**
- 기본값 사용

---

## 3. 테스트 결과

### 3.1 전체 결과
- **상태**: FAILED (Exit Code 99)
- **총 처리 요청**: 약 285,000+
- **실행 시간**: 19분 25초 (목표 19분 중)
- **완료 단계**: Stage 2 중간 (1,070 VUs)
- **Threshold 위반**: http_req_duration (P95 > 1000ms)

### 3.2 타임라인

| 시간 | VUs | 상태 | 비고 |
|------|-----|------|------|
| 00:00 - 01:06 | 530 | 정상 | 50,780 요청 처리 |
| 01:06 - 01:22 | 530 - 1,070 | 정상 | Stage 2 진입 |
| 01:22:45 | 1,070 | 경고 | DB 커넥션 풀 고갈 시작 |
| 01:25:29 | 1,070 | 장애 | Redirect Service 다운 |
| 01:28:51 | - | 실패 | 테스트 중단 (Threshold 위반) |

### 3.3 주요 에러

**DB 커넥션 풀 고갈 (01:22:45)**

```
HikariPool-1 - Connection is not available, request timed out after 30020ms
(total=10, active=10, idle=0, waiting=189)
```

**Redirect Service 장애 (01:25:29 ~ 종료)**
```
- connection reset by peer (다수)
- dial: i/o timeout (200+ 발생)
```

---

## 4. 병목 지점 분석

### 4.1 Primary Bottleneck: DB 커넥션 풀

**발견 시점**: 01:22:45 (테스트 시작 후 13분)
**부하 수준**: 1,070 VUs
**증상**:
- HikariCP 최대 커넥션 10개 모두 사용 중
- 189개 요청 대기 중
- 30초 타임아웃 발생

**영향**:
- URL Service 응답시간 급증
- 신규 URL 생성 지연
- Redirect Service 캐시 미스 증가

### 4.2 Secondary Bottleneck: Redirect Service

**발견 시점**: 01:25:29 (DB 문제 발생 3분 후)
**증상**:
- 연결 거부 (connection reset)
- 타임아웃 대량 발생

**추정 원인**:
1. URL Service 지연으로 인한 캐시 미스 증가
2. R2DBC 커넥션 풀 미설정으로 인한 DB 접근 문제
3. 네트워크 소켓 고갈 (macOS 파일 디스크립터 제한)
4. WebFlux 이벤트 루프 과부하

---

## 5. 시스템 한계점

### 5.1 안정적인 처리 용량
- **최대 VUs**: 530 (URL 20, Redirect 500, Stats 10)
- **예상 TPS**: 약 2,000 req/s
- **안정 운영 시간**: 16분 이상

### 5.2 한계 지점
- **VUs**: 1,070 (URL 50, Redirect 1,000, Stats 20)
- **지속 시간**: 약 3분 (01:22 ~ 01:25)
- **주 원인**: DB 커넥션 풀 부족

### 5.3 장애 발생 지점
- **VUs**: 1,070
- **시점**: 01:25:29 (DB 문제 발생 3분 후)
- **주 원인**: Redirect Service 완전 다운

---

## 6. 근본 원인

```
HikariCP 기본 설정 (10 커넥션)
    ↓
DB 커넥션 고갈 (01:22)
    ↓
URL Service 응답 지연 (30초 타임아웃)
    ↓
신규 URL 생성 차단
    ↓
Redirect Service 캐시 미스 증가
    ↓
전체 시스템 장애 (01:25)
```

---

## 7. 조치 사항

### 1. HikariCP 설정 추가

shortly-url-service/src/main/resources/application-local.yml:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 20
      connection-timeout: 20000
      idle-timeout: 300000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
```

shortly-click-service/src/main/resources/application-local.yml:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30
      minimum-idle: 10
      connection-timeout: 20000
```

### 2. R2DBC 풀 설정 추가**

shortly-redirect-service/src/main/resources/application.yml:
```yaml
spring:
  r2dbc:
    pool:
      initial-size: 20
      max-size: 50
      max-idle-time: 30m
      max-acquire-time: 20s
```
**작성일**: 2025-10-26
