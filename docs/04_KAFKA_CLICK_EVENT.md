## 1. 개요

### 1.1 목표
- 클릭 건수로 요금을 부과한다는 비즈니스 가정
- 클릭 이벤트는 과금 데이터로, 유실과 중복 모두 불가

### 1.2 전략
- Kafka At-Least-Once 전달 보장으로 유실 방지
- 애플리케이션 레벨 멱등성 처리로 중복 방지

---

## 2. 유실 방지

### 2.1 Producer

#### acks=all
- Leader가 응답 후 Replica 복제 전 장애 발생 시 메시지 유실
- 클릭 이벤트는 유실이 불가하므로 성능보다 안정성이 우선
- `acks=all` `min.insync.replicas=3` 설정으로 최소 3개 Replica 저장 보장

#### 재시도 + DLQ
- 일시적 네트워크 장애나 브로커 재시작 시 전송 실패
- 일시적 장애는 2분 내로 회복된다는 판단으로 `delivery.timeout.ms`을 2분으로 설정

### 2.2 Consumer

#### 수동 커밋 (batch)
- 자동 커밋은 메시지를 읽자마자 Offset 커밋 → DB 저장 중 예외 발생 시 재처리 불가
- DB 저장 완료 후에만 Offset 커밋하도록 수동 커밋 전환
- 다수의 리다이렉트 요청에 대응하기 위해 커밋 단위는 배치(batch)로 설정

#### 재시도 + DLQ
- 영구적 오류(데이터 포멧 오류, 비즈니스 규칙 위반)와 일시적 오류(네트워크 타임아웃)을 구분
- 영구적 오류: 즉시 DLQ에 저장
- 일시적 오류: 지수 백오프 적용
  - 100ms부터 시작하여 재시도마다 대기 시간을 2배씩 증가
  - 한 번의 대기는 최대 5초로 제한
  - 전체 재시도는 총 30초로 제한
---

## 3. 중복 방지

### 3.1 Producer

#### 멱등성 설정
- 네트워크 타임아웃으로 Producer가 ACK를 받지 못하면 재시도
- 브로커가 메시지를 받은 상태면 같은 메시지가 두 번 저장
- `enable.idempotence=true` 설정으로 브로커 중복 저장 방지

### 3.2 Consumer

#### 이벤트 고유 ID
- 오프셋 커밋 전 예외가 발생하면 같은 메시지가 중복 처리될 수 있음
- JMH 벤치마크로 Snowflake ID와 UUID 비교 테스트 진행(하단 `JMH 벤치마크 결과` 참고)
- 처리량과 지연 시간 측면에서 Snowflake가 약 2배 낮은 성능이지만 차이가 미세하고 메모리 효율성과 DB 인덱스를 고려했을 때 `Snowflake ID`가 적합하다 판단

#### DB 유니크 제약
- 애플리케이션 로직이 실패하더라도 DB 레벨에서 중복을 방어하기 위해 `event_id` 컬럼에 Unique 제약조건 추가

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

