## 1. 분산 환경에서 Caffeine L1 캐시 불일치

### 문제 상황
1. Redirect Service를 수평 확장 (3개 인스턴스) 시 각 인스턴스가 독립적인 Caffeine L1 캐시 보유.
2. URL Service에서 새로운 short code 생성 → Kafka 이벤트 발행.
3. Redirect Service 인스턴스 A는 이벤트를 소비하여 캐시 업데이트.
4. 인스턴스 B, C는 이벤트를 못 받으면 캐시 불일치 발생 (확률적).
5. 사용자가 인스턴스 B로 요청 시 캐시 미스 → Redis 조회 → 성능 저하.

### 해결 방안

#### 1. Kafka Consumer Group 설정 검토
- Redirect Service의 모든 인스턴스가 동일한 Consumer Group에 속하도록 설정.
- 각 인스턴스가 이벤트를 수신하도록 보장.

#### 2. Broadcast 토픽 패턴
- `url-created` 이벤트를 모든 Redirect Service 인스턴스에 브로드캐스트.
- 각 인스턴스가 독립적인 Consumer Group으로 동일 이벤트 수신.

```yaml
spring:
  kafka:
    consumer:
      group-id: redirect-service-${random.uuid}  # 인스턴스별 고유 Group ID
```

#### 3. Cache Invalidation 메시지
- Redis Pub/Sub로 캐시 무효화 메시지 전파.
- URL 업데이트 시 모든 인스턴스의 Caffeine 캐시 제거.
