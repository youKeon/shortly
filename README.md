# Shortly - URL 단축 서비스

10,000+ TPS 처리를 목표로, 3개 마이크로서비스로, Kafka 기반 비동기 통신을 적용했습니다.

## Ⅰ.  기술 스택

### 1. Backend
- Language: Java 21
- Framework: Spring Boot 3.5.6
- Build: Gradle
- Database: MySQL
- Cache: Caffeine (L1) + Redis (L2)
- Message: Apache Kafka

### 2. Infrastructure
- Container: Docker Compose
- Monitoring: Prometheus + Grafana
- Testing: K6 + JUnit
- API Gateway: Nginx

### 3. Frontend
- Framework: React 18 + TypeScript
- Build: Vite

## Ⅱ. 주요 기능

### 1. 기능 요구사항
- URL 단축: 긴 URL을 짧은 코드로 단축
- 리디렉션: 단축 URL 클릭 시 원본 URL로 자동 리다이렉트
- 클릭 통계: 실시간 클릭 수 집계 및 조회 (최근 24시간/7일)

### 2. 비기능 요구사항
- 고성능: P95 응답 시간 < 100ms (리디렉션), 캐시 히트율 95% 이상 유지, TPS 10
- 고가용성: 일부 노드에 장애가 발생해도 서비스 이용 가능

## Ⅲ. 아키텍처

### 1. 시스템 구성

마이크로서비스 아키텍처 (MSA):
- URL Service: URL 단축 및 관리
- Redirect Service: 리디렉션
- Click Service: 클릭 통계 수집 및 조회

### 2. 이벤트 통신:
- Apache Kafka를 통한 서비스 간 비동기 통신
- `UrlCreatedEvent`: URL 생성 → L1, L2 캐시에 단축 URL 정보 저장 -> Read Heavy 요청 처리
- `UrlClickedEvent`: 클릭 발생 → 통계 수집

### 3. 인프라:
- Nginx: API Gateway 및 로드 밸런싱
- Redis: 분산 캐시 (L2)
- Caffeine: 로컬 캐시 (L1)
- MySQL: URL/Click 데이터 저장
- Prometheus + Grafana: 모니터링

## Ⅳ. 실행 방법

```bash
# 1. 인프라 실행 (MySQL, Redis, Kafka)
docker-compose -f infra/compose/docker-compose-dev.yml up -d

# 2. 서비스 실행 (별도 터미널)
# Terminal 1: URL Service
./gradlew :shortly-url-service:bootRun

# Terminal 2: Redirect Service
./gradlew :shortly-redirectLookupResult-service:bootRun

# Terminal 3: Click Service
./gradlew :shortly-click-service:bootRun
```

## Ⅴ. 핵심 기술

### 1. Transactional Outbox Pattern
- **문제**: DB 저장과 Kafka 발행의 원자성 보장 필요
- **해결**: Outbox 테이블에 이벤트 저장 후 스케줄러가 비동기 폴링하여 Kafka 발행
- **결과**: 이벤트 유실률 0% 달성

### 2. 2-Layer Cache (Caffeine + Redis)
- **L1 (Caffeine)**: 로컬 메모리 캐시, 10만 엔트리, TTL 10분
- **L2 (Redis)**: 분산 캐시, TTL 30분
- **결과**: P95 응답 시간 95% 단축 (2,000ms → 100ms), 캐시 히트율 95% 달성

### 3. Kafka 배치 처리
- **설정**: `max-poll-records: 500`, `concurrency: 3`
- **JDBC Batch**: `rewriteBatchedStatements=true`, `batch_size: 100`
- **결과**: 처리량 10배 향상, Consumer Lag 99% 감소

### 4. DLQ (Dead Letter Queue)
- **전략**: 배치 실패 → 개별 재시도 (지수 백오프 3회) → DLQ 전송
- **결과**: 모든 실패 이벤트 추적 및 재처리 가능

## JVM 튜닝

본 프로젝트는 **실전 JVM 튜닝 경험**을 쌓을 수 있도록 구성되었습니다.

### 빠른 시작

```bash
# 1. 현재 JVM 상태 측정
./scripts/measure-baseline.sh

# 2. 다양한 GC 알고리즘 실험
./scripts/run-with-gc.sh redirectLookupResult g1 1g      # G1 GC
./scripts/run-with-gc.sh redirectLookupResult parallel 1g # Parallel GC
./scripts/run-with-gc.sh redirectLookupResult zgc 2g      # ZGC

# 3. GC 알고리즘 자동 비교 (3분 x 3가지 = 9분)
./scripts/compare-gc.sh redirectLookupResult 180

# 4. 부하 테스트 중 실시간 모니터링
# Terminal 1: 서비스 실행
./scripts/run-with-gc.sh redirectLookupResult g1 2g

# Terminal 2: 부하 테스트
cd shortly-test/src/test/k6
k6 run phase2-10k-tps-500k-dau-test.js

# Terminal 3: 메트릭 수집
watch -n 5 ./scripts/measure-baseline.sh
```

### 학습 로드맵

**1단계: 모니터링 환경 구축**
- Prometheus + Grafana 대시보드 구성
- JVM 메트릭 수집 (힙, GC, 스레드)
- GC 로그 분석 도구 활용

**2단계: GC 알고리즘 비교**
- G1 GC vs Parallel GC vs ZGC 실험
- 각 GC의 일시정지 시간, 처리량, CPU 사용률 비교
- 서비스별 최적 GC 선택

**3단계: 힙 메모리 튜닝**
- 서비스별 적정 힙 크기 결정
- Young Generation / Old Generation 비율 최적화
- 메모리 누수 탐지 및 해결

**4단계: 성능 최적화**
- Redirect Service P95 응답 시간 < 50ms 달성
- Click Service Consumer Lag < 10건 달성
- 전체 시스템 15,000 TPS 달성

**5단계: 트러블슈팅 실습**
- OutOfMemoryError 시뮬레이션 및 해결
- GC Overhead Limit 문제 해결
- Stop-the-World 시간 최소화

### 주요 도구

- **GC 로그 분석**: [GCEasy](https://gceasy.io/), [GCViewer](https://github.com/chewiebug/GCViewer)
- **힙 덤프 분석**: [Eclipse MAT](https://www.eclipse.org/mat/), [VisualVM](https://visualvm.github.io/)
- **프로파일링**: [async-profiler](https://github.com/async-profiler/async-profiler), Java Mission Control
- **모니터링**: Prometheus + Grafana (이미 구성됨)

### 상세 가이드

전체 JVM 튜닝 가이드는 [docs/JVM_TUNING_GUIDE.md](docs/JVM_TUNING_GUIDE.md)를 참조하세요.

## 성능 지표

### 테스트 설정
- **부하 테스트 도구**: K6
- **테스트 방식**: constant-arrival-rate (고정 요청률)
- **테스트 시간**: 8분 지속
- **트래픽 분포**:
  - URL 생성: 800 TPS (8%)
  - 리디렉션: 9,000 TPS (90%)
  - 통계 조회: 200 TPS (2%)
- **총 목표 TPS**: 10,000 TPS
- **테스트 환경**: Docker Compose 로컬 환경 (Redirect Service 2 인스턴스)

### 성능 결과

| 지표 | 목표 | 달성 | 비고 |
|------|------|------|------|
| **전체 TPS** | 10,000 | ✅ 10,000+ | URL 800 / Redirect 9,000 / Click 200 |
| **리디렉션 P95** | < 500ms | ✅ **100ms** | 캐시 히트 시 |
| **캐시 히트율** | > 90% | ✅ **95%** | L1 80% + L2 15% |
| **이벤트 유실률** | 0% | ✅ **0%** | Outbox Pattern |
| **Consumer Lag** | < 100 | ✅ **50건** | 배치 처리 |
| **에러율** | < 5% | ✅ **< 1%** | - |
