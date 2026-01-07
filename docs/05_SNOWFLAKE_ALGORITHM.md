## 1. 문제 배경

### 1.1 기존 방식의 한계
**구현:**
```java
// 기존: SHA-256 해싱 방식
String input = originalUrl + System.nanoTime();
String hash = SHA256(input);
String shortCode = Base62.encode(hash).substring(0, 6);
```

**발생한 문제:**
- **높은 충돌률**: 5만 건 요청 시 639건 충돌 발생 (충돌률 1.3%)
- **DB 부하**: 충돌 발생 시 재시도로 인한 불필요한 DB 조회
- **성능 저하**: SHA-256 해싱 오버헤드 (5만 건 생성에 141ms)

**원인:**
- **substring(0, 6) 사용**: 256비트 해시를 6자로 축소하면서 충돌 확률 급증
- **nanoTime의 한계**: 동일 nanosecond 내에 여러 요청이 같은 URL을 요청하면 동일 해시 생성

---

## 2. 해결 전략 비교

### 2.1 UUID

**장점:**
- 128비트 무작위 값으로 충돌 확률 극히 낮음 (2^64 = 1.8×10^19)
- JDK 표준 API로 간편한 구현

**단점:**
- **길이 문제**: UUID를 Base62 인코딩해도 22자
  - 128bit → Base62 → 약 22자 (`2^128 ≈ 62^22`)
  - 6자로 축소 시 기존 방식과 동일한 충돌 문제
- **시간순 정렬 불가**: 무작위 생성으로 DB 인덱스 비효율
- **GC 부하**: String 객체 생성으로 메모리 할당 (96 bytes/op, 920 MB/sec)

### 2.2 Redis Atomic Counter

**장점:**
- 순차 증가로 충돌 없음
- 구현 단순 (`INCR` 명령 한 줄)

**단점:**
- **성능 병목**: 매 요청마다 네트워크 I/O 발생 (1-2ms)
- **단일 장애점**: Redis 장애 시 전체 서비스 중단
  - Redis Sentinel/Cluster → 인프라 복잡도 증가
- **보안 이슈**: 순차 ID는 총 URL 개수 노출

### 2.3 Snowflake Algorithm (채택)

**선택 이유:**
1. 분산 환경에서도 고유성 보장
2. 외부 의존 없이 bitwise 연산만 수행하여 고성능
3. Timestamp 기반으로 높은 DB 인덱스 효율

---

## 3. Snowflake Algorithm 검증 테스트

> **전체 테스트 코드**: [SnowflakeIdGeneratorConcurrencyTest.java](../shortly-test/src/test/java/com/io/shortly/test/unit/id/SnowflakeIdGeneratorConcurrencyTest.java)

### 3.1 동시성 테스트

**테스트 시나리오:**
- 단일 스레드 10만개 생성 → 중복 검증
- 멀티스레드 10만개 동시 생성 (10개 스레드 × 1만개)
- 멀티스레드 10만개 동시 생성 (20개 스레드 × 5천개)
- 서로 다른 Worker/Datacenter ID 조합 검증

**결과:**
- 단일 스레드 10만개: 중복 0건
- 멀티스레드 10만개 (10 스레드): 중복 0건, 30초 이내 완료
- 멀티스레드 10만개 (20 스레드): 중복 0건, 30초 이내 완료
- 서로 다른 워커 2만개: 중복 0건

### 3.2 성능 테스트

**테스트 시나리오:**
- 단일 스레드 100만개 생성 (목표: 10초 이내, 100K TPS 이상)
- 멀티스레드 100만개 생성 (10개 스레드 동시)
- 순간 burst 4096개 생성 (같은 밀리초)

**결과:**
- 단일 스레드 100만개: **2.4초** 완료
- 처리량: **약 417K TPS** (목표 100K TPS 대비 4배 초과)
- 멀티스레드 100만개: 30초 이내 완료, 중복 0건
- Burst 4096개: 100ms 이내 완료

### 3.3 선형 증가 보장

**테스트 시나리오:**
- 1만개 ID 생성 후 순차 검증

**결과:**
- 1만개 ID 모두 선형 증가
- Timestamp 기반 생성으로 시간순 정렬 보장

---

## 4. Snowflake Algorithm vs UUID JMH 벤치마크 결과

### 4.1 처리량 (Throughput)

| 방식 | ops/ns | ops/sec | 배율 |
|------|--------|---------|------|
| **Snowflake** | 0.004 | **4,000,000** | 1.0x |
| UUID | 0.010 | 10,000,000 | 2.5x |

- UUID가 2.5배 빠르지만 둘 다 초당 수백만 개 수준으로 충분히 빠름
- 목표 TPS(100K)를 40배 초과

### 4.2 지연 시간 (Latency)

| 방식 | 평균 지연 | 표준편차 |
|------|----------|---------|
| **Snowflake** | **243.993 ns/op** | ± 0.210 |
| UUID | 100.803 ns/op | ± 38.821 |

- UUID가 2.4배 빠르지만 둘 다 나노초 단위로 무시 가능
- Snowflake의 표준편차가 낮음

### 4.3 메모리 할당 (JVM Heap)

| 방식 | 할당/op | 초당 할당 | GC 횟수 | GC 시간 |
|------|---------|----------|---------|---------|
| **Snowflake** | **≈ 0 B/op** | **≈ 0 MB/sec** | **0회** | 0ms |
| UUID | 96 B/op | 920 MB/sec | 3회 | 3ms |

- **Snowflake의 우위**: primitive `long` 타입으로 힙 할당 없음
- UUID는 String 객체 생성으로 초당 920MB 할당 → GC 압박
- 고부하 환경에서 Snowflake가 GC pause 없어 안정적

---

## 부록. 구현 파일 위치

| 파일 | 경로 | 설명 |
|------|------|------|
| **Snowflake ID 생성기** | `shortly-shared-kernel/.../UniqueIdGeneratorSnowflakeImpl.java` | 64bit ID 생성 로직 |
| **Node ID 관리** | `shortly-shared-kernel/.../NodeIdManager.java` | Redis 기반 Node ID 자동 할당 |
| **Short Code 생성** | `shortly-url-service/.../ShortUrlGeneratorSnowflakeImpl.java` | Snowflake ID → Base62 변환 |
| **동시성 테스트** | `shortly-test/.../SnowflakeIdGeneratorConcurrencyTest.java` | 멀티스레드 충돌 방지 검증 |
| **JMH 벤치마크** | `shortly-test/.../IdGeneratorBenchmark.java` | UUID vs Snowflake 성능 비교 |
