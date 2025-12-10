## 1. Producer

### 1.1 acks: all
- Leader 브로커만 확인하면 Follower 복제 전 Leader 장애 시 메시지 유실
- 모든 replica 확인으로 브로커 장애 시에도 메시지 보존

### 1.2 enable.idempotence: true
- 네트워크 타임아웃 후 재시도 시 동일 메시지가 중복 발행될 수 있음
- Producer ID + Sequence Number로 브로커가 중복 메시지 자동 감지 및 제거

### 1.3 재시도 + DLQ
- 애플리케이션 레벨에서의 재시도로 일시적 장애 극복
- 100ms부터 시작해서 2배씩 증가하며 재시도
- 30초 이내에 성공하지 못하면 DLQ로 전송

## 2. Consumer

### 2.1 Offset 수동 커밋
- 자동 커밋은 메시지 수신 즉시 offset 커밋 → 처리 중 장애 시 메시지 유실
- 수동 커밋으로 처리 완료 후에만 offset 커밋

### 2.2 재시도 + DLQ
- DB 장애 등 일시적 오류 시 메시지 유실 방지를 위해 재시도
- 최종 실패 시 DLQ로 전송하여 메시지 보존 및 수동 재처리 가능
- `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` 활용하여 DLQ 구현

### 2.3 멱등성 처리
- At-Least-Once는 유실 방지를 위해 중복을 허용
- 중복 메시지로 인한 데이터 정합성 문제 방지를 위해 멱등성 처리 적용

#### Event ID
- UUID와 Snowflake ID 재사용 검토
- JMH 벤치마크 (100만 건 생성) 결과 기반으로 Snowflake ID 선택
  - 생성 시간:
    - UUID: 0.131μs
    - Snowflake ID: 0.244μs (+0.113μs)
  - 메모리 사용량:
    - UUID: 104.2 bytes
    - Snowflake ID: 10.3 bytes (10배 절감)

#### DB Unique Constraint
- `event_id` 컬럼에 Unique 제약조건
- 중복 이벤트 저장 시 예외 발생 → 무시 처리로 멱등성 보장
