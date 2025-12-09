## 1. 문제 배경
정상 흐름:
1. 리다이렉션 요청
2. 클릭 이벤트 발행
3. 클릭 이벤트 소비(DB에 클릭 기록 Insert)

문제 상황:
- `메시지 중복`: 네트워크 장애 시 Producer 재시도로 인한 중복 발행
- `메시지 유실`: Consumer 처리 중 장애 발생 시 offset만 커밋되는 경우

## 2. 해결 전략
### 2.1 Producer 신뢰성 보장
#### enable-idempotence: true
- Kafka Producer가 자동으로 중복 메시지를 감지하고 제거
- 각 Producer에 고유 ID 할당 (Producer ID)
- 각 메시지에 순차 번호 부여 (Sequence Number)
- Kafka 브로커가 `Producer ID + Sequence Number` 조합으로 중복 감지
- 이미 받은 메시지는 자동으로 무시 (ACK만 재전송)

#### acks=all
- Producer가 메시지를 보낸 후, replica가 확인할 때까지 대기
- 메시지 유실 방지: 브로커 장애 시에도 데이터 보존
- Leader 노드 장애 시 Follower 노드가 복제 전이면 메시지가 유실되는 문제를 예방

### 2.2 Consumer 멱등성 처리
#### 이벤트 멱등성 키
고려한 전략:
- UUID와 URL 단축에 사용되는 Snowflake ID 재활용을 고려 -> 벤치마크를 `Snowflake ID` 선택
- JMH 벤치마크로 100만 건 생성 결과
  - 생성 시간:
    - UUID: 0.131μs
    - Snowflake ID: 0.244μs (+0.113μs)
    - UUID 생성 속도가 0.113μs 더 빠르지만 무시할 수준이라고 판단
  - 메모리 사용량:
    - UUID: 104.2 bytes (x10.1)
    - Snowflake ID: 10.3 bytes
    - 메모리 사용량은 Snowflake ID가 약 10배 더 절감

#### enable-auto-commit: false
- 수동 커밋 설정
- ack-mode를 batch로 설정하여 100개 단위로 커밋

#### 2.3 DLQ
- `DefaultErrorHandler`와 `DeadLetterPublishingRecoverer`를 조합하여 DLQ 구현
- 지수 백오프를 적용: 100ms 시작, 2배씩 증가, 총 3초

## 3. 결과

