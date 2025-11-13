# Shortly - Enterprise URL Shortener Service

<div align="center">

**고성능 마이크로서비스 기반 URL 단축 서비스**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.6-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://reactjs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.2-blue.svg)](https://www.typescriptlang.org/)
[![Kafka](https://img.shields.io/badge/Kafka-3.7-black.svg)](https://kafka.apache.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)

</div>

---

## 📋 목차

- [개요](#-개요)
- [주요 기능](#-주요 기능)
- [시스템 아키텍처](#-시스템-아키텍처)
- [기술적 하이라이트](#-기술적-하이라이트)
- [기술 스택](#-기술-스택)
- [빠른 시작](#-빠른-시작)
- [API 문서](#-api-문서)
- [프로젝트 구조](#-프로젝트-구조)
- [모니터링](#-모니터링)

---

## 🎯 개요

**Shortly**는 프로덕션 환경을 고려한 엔터프라이즈급 URL 단축 서비스입니다.

마이크로서비스 아키텍처와 이벤트 기반 설계를 통해 **확장성**, **고가용성**, **고성능**을 동시에 달성했습니다.

### 핵심 가치

- ⚡ **초저지연 리다이렉션**: 2단계 캐싱 전략으로 밀리초 단위 응답
- 🔄 **이벤트 기반 아키텍처**: Kafka와 Outbox 패턴으로 서비스 간 느슨한 결합
- 📊 **실시간 분석**: URL 클릭 추적 및 통계 제공
- 🛡️ **데이터 일관성 보장**: Outbox 패턴으로 이벤트 발행 신뢰성 확보
- 📈 **확장 가능한 설계**: Database per Service 패턴으로 독립적 확장

---

## ✨ 주요 기능

| 기능 | 설명 |
|------|------|
| **URL 단축** | Snowflake ID 기반 고유 Short Code 생성 |
| **고속 리다이렉션** | Redis + Caffeine 2단계 캐싱으로 초저지연 응답 |
| **클릭 분석** | 24시간/7일 통계 및 클릭 상세 정보 제공 |
| **이벤트 기반 처리** | Kafka를 통한 비동기 이벤트 처리 |
| **실시간 모니터링** | Prometheus + Grafana 대시보드 |
| **API 문서** | Swagger UI 제공 |

---

## 🏗️ 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                        Client (Browser)                          │
└────────────────────────┬────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Nginx Reverse Proxy                           │
│                        (Port 80)                                 │
└───┬─────────────────────┬─────────────────────┬─────────────────┘
    │                     │                     │
    ▼                     ▼                     ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐
│ URL Service  │  │   Redirect   │  │   Click Service          │
│  (Port 8081) │  │   Service    │  │   (Port 8083)            │
│              │  │ (Port 8082)  │  │                          │
│ - URL 생성   │  │ - 리다이렉트 │  │ - 클릭 추적              │
│ - Outbox 저장│  │ - 캐시 조회  │  │ - 통계 집계              │
└──────┬───────┘  └──────┬───────┘  └──────┬───────────────────┘
       │                 │                 │
       │ Outbox          │ Cache           │ Clicks
       │ Relay           │ Lookup          │ Data
       ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐
│   MySQL      │  │    Redis     │  │       MySQL              │
│ shortly_url  │  │   + Caffeine │  │   shortly_click          │
└──────┬───────┘  └──────────────┘  └──────────────────────────┘
       │
       │ Event Relay
       ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Apache Kafka                                │
│  Topics: URL_CREATED, URL_CLICKED                                │
└───┬─────────────────────────────────────────────────────────┬───┘
    │                                                         │
    │ Subscribe                                               │ Publish
    ▼                                                         │
┌──────────────┐                                             │
│   Redirect   │ ◄───────────────────────────────────────────┘
│   Service    │    Publish: URL_CLICKED
│              │
│   Click      │ ◄─── Subscribe: URL_CLICKED
│   Service    │
└──────────────┘
```

### 이벤트 플로우

```
1. URL 생성 플로우
   User → URL Service → MySQL (URL + Outbox)
                     → Scheduler (Outbox Relay)
                     → Kafka (URL_CREATED)
                     → Redirect Service (Cache Update)

2. 리다이렉션 플로우
   User → Redirect Service → Caffeine Cache (L1) → Redis (L2) → MySQL (Fallback)
                          → Kafka (URL_CLICKED)
                          → Click Service (Analytics)
```

---

## 🚀 기술적 하이라이트

### 1. **Outbox 패턴을 통한 데이터 일관성**

트랜잭션 일관성과 이벤트 발행 신뢰성을 동시에 보장합니다.

```java
@Transactional
public void createUrl() {
    // 1. DB에 URL 저장
    urlRepository.save(url);

    // 2. 같은 트랜잭션 내에서 Outbox에 이벤트 저장
    outboxRepository.save(event);

    // 3. 별도 스케줄러가 Outbox → Kafka로 안전하게 전달
}
```

**장점**:
- 트랜잭션 범위 내에서 이벤트 저장 → 메시지 유실 방지
- DB와 메시지 브로커 간 원자성 보장
- 재시도 메커니즘으로 이벤트 전달 보장

### 2. **2단계 캐싱 전략**

극도로 빠른 리다이렉션을 위한 다층 캐시 구조입니다.

```
Request → Caffeine (Local, 100ms TTL)
          ↓ Miss
          → Redis (Distributed, 3600s TTL)
            ↓ Miss
            → MySQL (Fallback)
```

**성능**:
- **L1 (Caffeine)**: 나노초 단위 응답
- **L2 (Redis)**: 밀리초 단위 응답
- **Cache Hit Rate**: 95%+ 달성 목표

### 3. **Database per Service**

각 마이크로서비스가 독립적인 데이터베이스를 소유합니다.

```
URL Service     → shortly_url (MySQL:3307)
Click Service   → shortly_click (MySQL:3309)
Redirect Service → Redis only (읽기 최적화)
```

**장점**:
- 서비스 독립성: 각 서비스가 독립적으로 확장 가능
- 기술 다양성: 서비스별 최적 DB 선택 가능
- 장애 격리: 한 서비스 DB 장애가 다른 서비스에 영향 없음

### 4. **Snowflake ID 기반 분산 ID 생성**

분산 환경에서 충돌 없는 고유 ID를 생성합니다.

```
64bit = 1bit(unused) + 41bit(timestamp) + 10bit(node) + 12bit(sequence)
```

**특징**:
- 시간순 정렬 가능 (timestamp 기반)
- 초당 400만개+ ID 생성 가능
- 중앙 DB 없이 분산 생성

### 5. **배치 처리 최적화**

Click Service에서 JDBC 배치를 활용한 대량 Insert 최적화입니다.

```java
spring.jpa.properties.hibernate.jdbc.batch_size=100
spring.jpa.properties.hibernate.order_inserts=true
```

**성능**: 개별 Insert 대비 **10배 이상** 처리량 향상

---

## 🛠️ 기술 스택

### Backend

| 카테고리 | 기술 | 버전 | 용도 |
|----------|------|------|------|
| **Language** | Java | 21 | 주 언어 |
| **Framework** | Spring Boot | 3.5.6 | 애플리케이션 프레임워크 |
| **Build** | Gradle | 8.x | 빌드 도구 |
| **Database** | MySQL | 8.0 | 관계형 데이터베이스 |
| **Cache** | Redis | 7.x | 분산 캐시 |
| **Cache** | Caffeine | - | 로컬 캐시 (L1) |
| **Messaging** | Apache Kafka | 3.7 | 이벤트 스트리밍 |
| **ORM** | Spring Data JPA | - | 데이터 접근 계층 |
| **Monitoring** | Prometheus | - | 메트릭 수집 |
| **Monitoring** | Grafana | - | 시각화 대시보드 |
| **API Docs** | SpringDoc OpenAPI | - | Swagger UI |

### Frontend

| 카테고리 | 기술 | 버전 | 용도 |
|----------|------|------|------|
| **Framework** | React | 18 | UI 프레임워크 |
| **Language** | TypeScript | 5.2 | 타입 안정성 |
| **Build** | Vite | 5.x | 빌드 도구 |
| **Compiler** | SWC | - | 고속 컴파일러 |
| **Icons** | Lucide React | - | 아이콘 라이브러리 |
| **Toast** | Sonner | - | 알림 UI |

### Infrastructure

| 카테고리 | 기술 | 용도 |
|----------|------|------|
| **Container** | Docker | 컨테이너화 |
| **Orchestration** | Docker Compose | 로컬 개발 환경 |
| **Reverse Proxy** | Nginx | API Gateway / 로드 밸런싱 |
| **CI/CD** | Gradle | 빌드 자동화 |

---

## 🚀 빠른 시작

### 사전 요구사항

- **Java**: 21 이상
- **Docker & Docker Compose**: 최신 버전
- **Node.js**: 18 이상 (Frontend 개발 시)
- **Gradle**: 8.x (또는 Wrapper 사용)

### 1. 저장소 클론

```bash
git clone https://github.com/youKeon/shortly.git
cd shortly
```

### 2. 인프라 실행 (MySQL, Redis, Kafka)

```bash
cd infra/compose
docker-compose -f docker-compose-dev.yml up -d
```

**실행되는 컨테이너**:
- MySQL (URL Service): `localhost:3307`
- MySQL (Click Service): `localhost:3309`
- Redis: `localhost:6379`
- Kafka: `localhost:9092`
- Zookeeper: `localhost:2181`

### 3. 백엔드 서비스 빌드 및 실행

```bash
# 프로젝트 루트에서
./gradlew clean build

# 각 서비스 실행 (별도 터미널에서)
# Terminal 1: URL Service
./gradlew :shortly-url-service:bootRun

# Terminal 2: Redirect Service
./gradlew :shortly-redirect-service:bootRun

# Terminal 3: Click Service
./gradlew :shortly-click-service:bootRun
```

### 4. 프론트엔드 실행 (선택)

```bash
cd frontend
npm install
npm run dev
```

Frontend: `http://localhost:5173`

### 5. 서비스 확인

| 서비스 | URL |
|--------|-----|
| URL Service | http://localhost:8081 |
| Redirect Service | http://localhost:8082 |
| Click Service | http://localhost:8083 |
| Nginx Gateway | http://localhost (Docker Compose 전체 실행 시) |

### 6. API 테스트

```bash
# URL 단축
curl -X POST http://localhost:8081/api/v1/urls/shorten \
  -H "Content-Type: application/json" \
  -d '{"originalUrl": "https://github.com/youKeon/shortly"}'

# 응답 예시
# {"shortCode": "abc123", "originalUrl": "https://github.com/youKeon/shortly"}

# 리다이렉션 (브라우저에서)
curl -L http://localhost:8082/r/abc123

# 통계 조회
curl http://localhost:8083/api/v1/analytics/abc123/stats
```

---

## 📚 API 문서

### URL Service (Port 8081)

#### POST `/api/v1/urls/shorten`
긴 URL을 단축 코드로 변환합니다.

**Request**:
```json
{
  "originalUrl": "https://example.com/very/long/url"
}
```

**Response**:
```json
{
  "shortCode": "abc123",
  "originalUrl": "https://example.com/very/long/url",
  "createdAt": "2025-11-13T10:00:00Z"
}
```

#### GET `/api/v1/urls/{shortCode}`
Short Code로 원본 URL을 조회합니다 (Fallback용).

**Response**:
```json
{
  "shortCode": "abc123",
  "originalUrl": "https://example.com/very/long/url",
  "createdAt": "2025-11-13T10:00:00Z"
}
```

---

### Redirect Service (Port 8082)

#### GET `/r/{shortCode}`
단축 URL로 원본 URL로 리다이렉션합니다.

**Response**: `302 Found` with `Location` header

---

### Click Service (Port 8083)

#### GET `/api/v1/analytics/{shortCode}/stats`
URL의 클릭 통계를 조회합니다.

**Response**:
```json
{
  "shortCode": "abc123",
  "totalClicks": 1523,
  "last24Hours": 127,
  "last7Days": 834,
  "clicksPerDay": {
    "2025-11-13": 127,
    "2025-11-12": 98,
    ...
  }
}
```

#### GET `/api/v1/analytics/{shortCode}/clicks?limit=100`
최근 클릭 상세 정보를 조회합니다 (최대 100개).

**Response**:
```json
{
  "shortCode": "abc123",
  "clicks": [
    {
      "timestamp": "2025-11-13T10:30:00Z",
      "userAgent": "Mozilla/5.0...",
      "ipAddress": "192.168.1.1",
      "referer": "https://google.com"
    },
    ...
  ]
}
```

---

### Swagger UI

각 서비스의 Swagger UI에서 대화형 API 문서를 확인할 수 있습니다.

- URL Service: http://localhost:8081/swagger-ui.html
- Redirect Service: http://localhost:8082/swagger-ui.html
- Click Service: http://localhost:8083/swagger-ui.html

---

## 📁 프로젝트 구조

```
shortly/
├── shortly-url-service/          # URL 단축 서비스
│   ├── src/main/java/.../url/
│   │   ├── api/                  # REST API 컨트롤러
│   │   ├── domain/               # 도메인 모델 (URL, Outbox)
│   │   ├── service/              # 비즈니스 로직
│   │   ├── repository/           # 데이터 접근
│   │   └── config/               # 설정 (Kafka, JPA 등)
│   └── build.gradle
│
├── shortly-redirect-service/     # 리다이렉션 서비스
│   ├── src/main/java/.../redirect/
│   │   ├── api/                  # 리다이렉트 컨트롤러
│   │   ├── cache/                # 캐시 설정 (Redis, Caffeine)
│   │   ├── consumer/             # Kafka 컨슈머
│   │   └── service/              # 리다이렉트 로직
│   └── build.gradle
│
├── shortly-click-service/        # 클릭 분석 서비스
│   ├── src/main/java/.../click/
│   │   ├── api/                  # 분석 API 컨트롤러
│   │   ├── domain/               # Click 도메인
│   │   ├── consumer/             # Kafka 컨슈머
│   │   └── service/              # 집계 로직
│   └── build.gradle
│
├── shortly-shared-kernel/        # 공유 라이브러리
│   ├── event/                    # 이벤트 정의
│   │   ├── UrlCreatedEvent.java
│   │   └── UrlClickedEvent.java
│   ├── exception/                # 공통 예외
│   └── config/                   # 공통 설정
│
├── shortly-test/                 # 테스트 유틸리티
│
├── frontend/                     # React 프론트엔드
│   ├── src/
│   │   ├── features/             # 기능별 모듈
│   │   ├── shared/               # 공유 컴포넌트
│   │   └── widgets/              # 재사용 위젯
│   └── package.json
│
├── infra/                        # 인프라 구성
│   ├── compose/
│   │   ├── docker-compose-dev.yml        # 개발용
│   │   ├── docker-compose-prod.yml       # 프로덕션용
│   │   └── docker-compose-monitoring.yml # 모니터링
│   ├── nginx/
│   │   └── nginx.conf            # Nginx 설정
│   └── monitoring/
│       ├── prometheus.yml        # Prometheus 설정
│       └── grafana/              # Grafana 대시보드
│
├── build.gradle                  # 루트 빌드 설정
├── settings.gradle               # 멀티모듈 설정
└── README.md
```

### 모듈별 역할

| 모듈 | 역할 | 포트 | 데이터베이스 |
|------|------|------|--------------|
| **shortly-url-service** | URL 생성 및 Outbox 관리 | 8081 | MySQL (shortly_url) |
| **shortly-redirect-service** | 고속 리다이렉션 | 8082 | Redis (Cache only) |
| **shortly-click-service** | 클릭 추적 및 분석 | 8083 | MySQL (shortly_click) |
| **shortly-shared-kernel** | 공통 이벤트/예외/설정 | - | - |

---

## 📊 모니터링

### Prometheus + Grafana

모니터링 스택을 실행하여 실시간 메트릭을 확인할 수 있습니다.

```bash
cd infra/compose
docker-compose -f docker-compose-monitoring.yml up -d
```

**접속 정보**:
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (기본 계정: admin/admin)

### 주요 메트릭

| 메트릭 | 설명 |
|--------|------|
| `http_server_requests_seconds` | HTTP 요청 지연시간 |
| `jvm_memory_used_bytes` | JVM 메모리 사용량 |
| `kafka_consumer_lag` | Kafka 컨슈머 지연 |
| `cache_gets_total` | 캐시 조회 횟수 |
| `cache_hit_ratio` | 캐시 히트율 |
| `jdbc_connections_active` | 활성 DB 커넥션 |

### Health Check

```bash
# URL Service
curl http://localhost:8081/actuator/health

# Redirect Service
curl http://localhost:8082/actuator/health

# Click Service
curl http://localhost:8083/actuator/health
```

---

## 🧪 테스트

```bash
# 전체 테스트 실행
./gradlew test

# 특정 모듈 테스트
./gradlew :shortly-url-service:test

# 통합 테스트
./gradlew integrationTest

# 테스트 커버리지 리포트
./gradlew jacocoTestReport
```

---

## 📝 개발 환경 설정

### Profile 설정

각 서비스는 `application.yml`에서 프로파일을 설정할 수 있습니다.

```yaml
# application-local.yml (개발용)
spring:
  profiles:
    active: local
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

# application-prod.yml (프로덕션용)
spring:
  profiles:
    active: prod
  jpa:
    show-sql: false
```

### 실행 시 프로파일 지정

```bash
# Local 프로파일로 실행
./gradlew :shortly-url-service:bootRun --args='--spring.profiles.active=local'

# Prod 프로파일로 실행
./gradlew :shortly-url-service:bootRun --args='--spring.profiles.active=prod'
```

---

## 🌟 주요 설계 패턴

| 패턴 | 적용 위치 | 목적 |
|------|-----------|------|
| **Outbox Pattern** | URL Service | 이벤트 발행 신뢰성 보장 |
| **CQRS** | 전체 아키텍처 | 읽기/쓰기 최적화 분리 |
| **Database per Service** | 각 마이크로서비스 | 서비스 독립성 확보 |
| **Cache-Aside** | Redirect Service | 캐시 조회 패턴 |
| **Event Sourcing (부분)** | Click Service | 이벤트 기반 집계 |
| **Circuit Breaker** | 서비스 간 통신 | 장애 전파 방지 |
| **Bulkhead** | Thread Pool 분리 | 리소스 격리 |

---

## 🔧 설정 가이드

### Kafka 토픽 생성

```bash
# Kafka 컨테이너 접속
docker exec -it kafka bash

# 토픽 생성
kafka-topics.sh --create --topic URL_CREATED \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

kafka-topics.sh --create --topic URL_CLICKED \
  --bootstrap-server localhost:9092 \
  --partitions 3 \
  --replication-factor 1

# 토픽 확인
kafka-topics.sh --list --bootstrap-server localhost:9092
```

### MySQL 데이터베이스 초기화

```bash
# URL Service DB
docker exec -it mysql-url mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS shortly_url;"

# Click Service DB
docker exec -it mysql-click mysql -uroot -proot -e "CREATE DATABASE IF NOT EXISTS shortly_click;"
```

---

## 🚀 프로덕션 배포

### Docker Compose로 전체 스택 실행

```bash
cd infra/compose
docker-compose -f docker-compose-prod.yml up -d
```

**포함 내용**:
- 모든 백엔드 서비스 (URL, Redirect, Click)
- MySQL, Redis, Kafka
- Nginx Reverse Proxy
- Prometheus, Grafana

### Nginx를 통한 단일 진입점

```
http://localhost/api/v1/urls/*      → URL Service (8081)
http://localhost/r/*                → Redirect Service (8082)
http://localhost/api/v1/analytics/* → Click Service (8083)
```

---

## 📈 성능 특성

### 예상 처리량

| 시나리오 | 예상 TPS | 지연시간 (P99) |
|----------|----------|----------------|
| URL 생성 | 1,000+ | < 100ms |
| 리다이렉션 (캐시 히트) | 10,000+ | < 5ms |
| 리다이렉션 (캐시 미스) | 1,000+ | < 50ms |
| 통계 조회 | 500+ | < 200ms |

*실제 성능은 하드웨어 사양에 따라 다를 수 있습니다.*

### 확장 전략

- **수평 확장**: 각 서비스를 독립적으로 스케일 아웃
- **캐시 확장**: Redis 클러스터 구성
- **DB 샤딩**: Short Code 기준 파티셔닝
- **Kafka 확장**: 파티션 수 증가로 처리량 향상

---

## 📜 라이선스

이 프로젝트는 개인 포트폴리오 목적으로 제작되었습니다.

---

## 👨‍💻 Contact

- GitHub: [@youKeon](https://github.com/youKeon)
- Project Link: [https://github.com/youKeon/shortly](https://github.com/youKeon/shortly)

---

<div align="center">

**Made with ❤️ for Learning Microservices Architecture**

</div>
