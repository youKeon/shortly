# Shortly - 고성능 URL 단축 서비스

## 기술 스택

### Backend
- **Java 21**
- **Spring Boot 3.5.6**
- **MySQL 8.0**
- **Redis 7.2** (Lettuce)

### Infrastructure

- **MQ**: Apache Kafka 3.5.1
- **Cache**: Caffeine(L1), Redis(L2)
- **Monitoring**: Prometheus, Grafana
- **Testing**: JUnit 5, k6

### 핵심 특징

- **MSA 아키텍처**: 3개의 독립적인 마이크로서비스
- **Event-Driven**: Apache Kafka 기반 비동기 통신
- **Multi-tier 캐싱**: Caffeine (L1) + Redis (L2)

## 아키텍처

### 시스템 아키텍처
<img width="2360" height="1071" alt="arch" src="https://github.com/user-attachments/assets/25b3645b-df59-42bf-bfdc-651ea3386a0e" />

### 워크플로우

#### 1. URL 단축

```mermaid
sequenceDiagram
  participant Client
  participant US as URL Service
  participant DB as MySQL (Outbox)
  participant Kafka
  Client ->> US: URL 단축 요청
  US ->> US: Snowflake ID 생성
  US ->> US: Base62 Encode
  US ->> DB: 단축 URL 저장
  US ->> DB: 단축 URL 생성 이벤트 저장
  US -->> Client: 단축 URL 반환
```

#### 2. URL 리다이렉션

```mermaid
sequenceDiagram
    participant Client
    participant RS as Redirect Service
    participant L1 as Caffeine (L1)
    participant L2 as Redis (L2)
    participant US as URL Service
    participant Kafka
    participant CS as Click Service
    participant DB_C as MySQL (Click)

    Client->>RS: 리다이렉션 요청

    alt L1 Cache Hit
        RS-->>Client: 302 Redirect
    else L1 Miss
        RS->>L2: L2 Cache(Redis) 조회
        alt L2 Cache Hit
            L2-->>RS: URL 반환
            RS->>L1: L1 Cache Update
            RS-->>Client: 302 Redirect
        else L2 Miss
            RS->>US: URL Service 요청(HTTP)
            US-->>RS: URL 반환
            RS->>L2: L2 Cache Update
            RS->>L1: L1 Cache Update
            RS-->>Client: 302 Redirect
        end
    end
```

## 문제 해결 경험

- [이벤트 특성을 고려한 MQ(RabbitMQ VS Kafka VS Redis(Stream)) 선택 과정](docs/01_SNOWFLAKE_ALGORITHM.md)
- [Snowflake Algorithm으로 URL 충돌률 1.3%→0%, 생성속도 3.2배 개선](docs/01_SNOWFLAKE_ALGORITHM.md)
- [Transactional Outbox Pattern으로 Kafka 장애 시 이벤트 유실 방지 (24만건 무손실)](docs/02_TRANSACTIONAL_OUTBOX_PATTERN.md)
- [2-Layer Cache (Redis+Caffeine) 전략으로 캐시 히트율 99.996%, P95 90ms 달성](docs/03_TWO_LAYER_CACHE_STRATEGY.md)
- [클릭 이벤트의 신뢰성을 보장하기 위한 Kafka 설정 과정](docs/04_KAFKA_CLICK_EVENT.md)
