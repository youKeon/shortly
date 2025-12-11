## 1. 문제 배경
> 클릭 이벤트는 URL 소유자에게 과금하는 핵심 데이터다. 이벤트가 유실되면 매출 손실로 직결되며, 중복 저장되면 과다 과금 문제가 발생한다.
>
> 이 문제를 해결하기 위해 Kafka의 At-Least-Once 전달 보장 모델을 선택했고, Producer와 Consumer 양쪽에서 메시지 유실을 방지하면서도 멱등성을 보장하는
구조를 설계했다.

---

## 2. Producer: 메시지 유실을 막기 위한 설정

### 2.1 acks=all

#### 문제 인식:

- Leader가 응답 후 Replica로 복제되기 전에 장애가 발생하면 메시지 유실

#### 옵션 비교:

| 설정         | 동작         | 장점         | 단점             |
|------------|------------|------------|----------------|
| `acks=0`   | ACK 대기 안 함 | 최고 성능      | 메시지 유실 가능      |
| `acks=1`   | Leader만 확인 | 성능과 안전성 절충 | Leader 장애 시 유실 |
| `acks=all` | 모든 ISR 확인  | 가장 안전      | 약간의 지연 발생      |

#### 선택:

- 과금 데이터라는 비즈니스 요구사항을 고려하여 성능보다 안전성을 선택
- `acks=all`을 선택하여 모든 In-Sync Replica가 메시지를 저장한 후에야 성공 응답을 받도록 설정

### 2.2 enable.idempotence=true

#### 문제 인식:

- 네트워크 타임아웃으로 Producer가 ACK를 받지 못하면 재시도
- 그런데 브로커가 메시지를 받은 상태면 같은 메시지가 두 번 저장

#### 선택:

- Kafka의 멱등성 Producer 기능을 활성화
- Producer ID + Sequence Number를 이용해 브로커가 중복 메시지를 자동으로 감지하고 ACK 반환

### 2.3 재시도 + DLQ: 브로커 전체 장애 대응

#### 문제 인식:

- 일시적 네트워크 장애나 브로커 재시작 시 메시지 전송이 실패할 수 있음

#### 선택:

- 애플리케이션 레벨에서 재시도 로직 추가
  - 전송 실패 시 최대 3회 재시도
  - 모두 실패하면 DLQ(Dead Letter Queue)로 메시지 전송
  - DLQ에 쌓인 메시지는 별도 모니터링 후 수동 재처리

---

## 3. Consumer: 처리 완료를 보장하기 위한 설정

### 3.1 배치 커밋

#### 문제 인식:

- Spring Kafka의 기본 설정은 자동 커밋 -> 메시지를 읽자마자 Offset 커밋
- 클릭 데이터를 DB에 저장하는 과정에서 예외가 발생하면 메시지는 이미 커밋되었기 때문에 재처리할 수 없음

#### 선택:

- **배치 커밋(`batch`)** 선택
- 대량의 클릭 이벤트를 처리하므로 메시지별 커밋은 오버헤드가 크다고 판단
- 배치 내 모든 메시지 처리 완료 후 한 번에 Offset 커밋하여 처리량을 높이면서도 안전성 보장

### 3.2 재시도 + DLQ

#### 문제 인식:

- DB 연결 끊김, Deadlock 등 일시적 장애는 재시도하면 해결 가능
- 영구적 실패(예: 잘못된 데이터 형식)는 재시도 의미가 없음

#### 선택:

- `DefaultErrorHandler`로 재시도 정책을 설정하고, 최종 실패 시 `DeadLetterPublishingRecoverer`로 DLQ에 메시지 보관
- 재시도 정책은 Producer와 동일 (최대 3회)
- 일시적 장애는 자동 복구, 영구적 실패는 DLQ에 보존하여 수동 분석 및 재처리 가능

---

## 4. 멱등성: 중복 메시지 방어

### 4.1 문제 인식

- At-Least-Once 모델은 메시지 유실은 막지만 중복은 허용
- 같은 클릭 이벤트를 두 번 저장하면 과다 과금 문제가 발생
- : 메시지의 고유 ID를 기반으로 중복 감지

### 4.2 메시지의 고유 ID 설정

#### UUID

- 간단하고 범용적
- 128bit로 메모리 사용량이 큼
- 시간 순서를 알 수 없음

#### Snowflake ID

- 64bit로 메모리 효율적
- Timestamp 기반이라 시간 순서 정렬 가능
- 분산 환경에서도 고유성 보장

#### 검증
> JMH 벤치마크로 테스트 진행
>- [테스트 코드](/shortly-test/src/jmh/java/com/io/shortly/test/benchmark/IdGeneratorBenchmark.java)
>- [테스트 결과](/shortly-test/src/jmh/java/com/io/shortly/test/benchmark/results.txt)

`처리량(Throughput)`:
- UUID: 0.010 ops/ns -> 초당 1,000만개
- Snowflake ID: 0.004 ops/ns -> 초당 400만개
- 비고: UUID가 2.5배 빠르지만 둘 다 충분히 많음

`지연 시간(Latency)`:
- UUID: 100.803 ± 38.821 ns/op
- Snowflake ID: 243.993 ± 0.210 ns/op
- 비고: UUID가 2.5배 빠르지만 둘 다 충분히 빠름

`메모리 할당(JVM)`:
- ID 생성당 메모리 할당
  - UUID: 96 bytes/op
  - Snowflake ID: 0 bytes/op
- 초당 메모리 할당
  - UUID: 920 MB/sec
  - Snowflake ID: 0 MB/sec
- GC 횟수
  - UUID: 3회
  - Snowflake ID: 0회
- 비고: Snowflake ID가 메모리 할당이 거의 없음



#### 결정: Snowflake ID

- 처리량과 지연 시간 측면에서 Snowflake가 약 2배 낮은 성능이지만 충분한 성능
- 메모리 효율성과 DB 인덱스 성능을 고려했을 때 `Snowflake ID`가 적합하다 판단

### 4.3 DB 유니크 제약
- 애플리케이션 로직이 실패하더라도 DB 레벨에서 중복을 방어하기 위해 `event_id` 컬럼에 Unique 제약조건 추가

## 5. 결과
### 5.1 테스트 환경
- 초당 12,621 번의 리다이렉트 요청
- 총 130만 건의 클릭 이벤트 발행

### 5.2 테스트 결과
- 데이터 유실 0건

