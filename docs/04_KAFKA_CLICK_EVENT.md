## 1. 개요

### 1.1 목표
- 클릭 건수로 요금을 부과한다는 비즈니스 가정
- 클릭 이벤트는 과금 데이터로, 유실과 중복 모두 불가

### 1.2 전략
- Kafka At-Least-Once 전달 보장으로 유실 방지
- 애플리케이션 레벨 멱등성 처리로 중복 방지

---

## 2. 유실 방지를 위한 설정

### 2.1 프로듀서

#### acks=all
- `acks=1`로 프로듀는 Broker Leader의 응답 확인(Ack)만 기다린다. 즉, 브로커의 Ack만으로 전송 완료 처리된다.
- 이 경우 리더가 메시지를 Ack를 보낸 후, Follower가 복제하기 전에 Leader에 장애가 발생하면 메시지가 손실된다.
- `acks=all` `replication.factor=3` `min.insync.replicas=2` 설정으로 최소 2개 이상의 Replica 저장을 보장했다.

### 2.2 컨슈머

#### 수동 커밋 (batch)
- 자동 커밋은 처리 여부와 무관하게 `auto.commit.interval.ms`에서 설정한 간격으로 오프셋을 커밋한다. 즉, 브로커에서 이벤트를 폴링만하고 처리하지 못 한 상태에서 장애가 발생하면 오프셋이 뒤로 밀려 메시지가 유실된다.
- 수동 커밋으로 전환하여 DB에 이벤트(클릭 데이터) 저장이 성공하면 오프셋을 커밋하도록 변경했다.
- 최대 초당 1만 건의 리다이렉트 요청에 대응하기 위해 커밋 단위는 배치(batch)로 설정했다.

#### 재시도 + DLQ
- 영구적 오류(데이터 포멧 오류, 비즈니스 규칙 위반)와 일시적 오류(네트워크 타임아웃)를 구분했다.
- 영구적 오류는 즉시 DLQ에 저장하고, 일시적 오류는 지수 백오프 적용 재시도한다.
- 재시도 텀은 100ms부터 시작하여 재시도마다 대기 시간을 2배씩 증가시킨다. 각 대기는 최대 5초로 제한하고 전체 재시도는 총 30초로 제한했다.

---

## 3. 중복 방지

### 3.1 프로듀서

#### 멱등성 설정
- 프로듀서가 브로커로 이벤트를 발행하면, 브로커는 ACK를 반환한다. 만약, 브로커가 메시지를 받은 상태에서 네트워크 타임아웃으로 ACK를 반환하지 못하면 프로듀서는 이벤트를 재전송한다.
- `enable.idempotence=true` 설정으로 브로커가 중복을 제거할 수 있다.
- 설정을 활성화하면 프로듀서는 레코드에 Producer Id(PID)와 Sequence Number(seq)를 붙여서 보낸다.
- 브로커는 이 PID외 seq로 중복 여부를 판단한다.

### 3.2 컨슈머

#### 이벤트 고유 ID
- 컨슈머는 이벤트를 소비하고 오프셋을 커밋한다. 만약, 오프셋 커밋 전 장애가 발생하면 같은 이벤트를 중복 처리한다.
- 컨슈머의 중복 소비를 방지하기 위해 eventId를 도입했다.
- eventId 후보로는 Snowflake ID와 UUID를 비교했고, JMH 벤치마크로 성능을 비교하여 Snowflake ID를 선택했다(하단 `JMH 벤치마크 결과` 참고).
- 처리량과 지연 시간 측면에서 Snowflake가 약 2배 낮은 성능이지만 차이가 미세하고 메모리 효율성과 DB 인덱스를 고려했을 때 `Snowflake ID`가 적합하다 판단했다.

#### DB 유니크 제약
- 애플리케이션 로직이 실패하더라도 DB 레벨에서 중복을 방어하기 위해 `event_id` 컬럼에 Unique 제약조건 추가했다.

---

## 4. 결과

### 4.1 테스트 환경
- 초당 12,621건 리다이렉트 요청, 총 130만 건 이벤트 발행

### 4.2 검증 결과
| 지표 | 결과 | 검증 방법 |
|------|------|-----------|
| 유실 | 0건 | Producer 발행 수 = DB 저장 수 |
| 중복 | 0건 | COUNT(event_id) = COUNT(DISTINCT event_id) |

---

## 부록. JMH 벤치마크 결과
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

