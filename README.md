# Shortly - 고성능 URL 단축 서비스

## 1. 개요

### 1.1 기능 요구사항
- URL 단축
  - 사용자는 원본 URL을 입력하면 단축 URL을 생성할 수 있다.
  - 단축 URL은 중복이 발생하면 안 된다.
- 리다이렉트
  - 사용자가 단축 URL에 접속하면 원본 리다이렉트된다.
- 통계 기능
  - 단축 URL 별 클릭 기록 조회 가능

### 1.2 비기능 요구사항
- `가용성`: 서비스가 다운되면 모든 URL 리다이렉션이 실패하기 때문에 시스템은 높은 가용성을 가져야 한다.
- `성능`: URL 리다이렉션은 최소한의 Latency로 처리되어야 한다.
- `무작위성`: 단축된 URL은 추측이 예측 불가능하게 생성되어야 한다.

### 1.3 기술 스택
- Java 21
- Spring Boot 3.5.6
- MySQL 8.0
- Redis 7.2 (Lettuce)
- Kafka

## 2. 아키텍처

### 2.1 시스템 아키텍처

![img_1.png](img_1.png)

### 2.2 워크플로우

#### URL 단축

```mermaid
sequenceDiagram
  participant Client
  participant US as URL Service
  participant DB as MySQL
  participant Redis
  participant RS as Redirect Service

  Client ->> US: URL 단축 요청
  US ->> US: Snowflake ID 생성
  US ->> US: Base62 Encode
  US ->> DB: 단축 URL 저장
  US ->> Redis: URL 생성 이벤트 Pub
  US ->> Client: 단축 URL 반환

  Redis -->> RS: 이벤트 Sub
  RS ->> RS: L1(Caffeine) + L2(Redis) 캐시 저장
```

#### URL 리다이렉션

```mermaid
sequenceDiagram
    participant Client
    participant RS as Redirect Service
    participant L1 as Caffeine (L1)
    participant L2 as Redis (L2)
    participant US as URL Service

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

## 3. 문제 해결 경험

- [클릭 이벤트의 신뢰성을 보장하기 위한 Kafka 설정 최적화](docs/04_KAFKA_CLICK_EVENT.md)
- [k6 부하 테스트를 통한 성능 개선 과정](docs/06_LOAD_TEST.md)
- [단축 URL 특성을 고려한 캐시 만료 정책 선택(TinyLFU VS LFU VS LRU)](docs/02_CACHE_EVICTION.md)
- [Caffeine Cache 동기화 메커니즘을 활용한 Cache Stampede 문제 해결](docs/03_CACHE_STAMPEDE.md)
- [메세지 큐 선택 과정(RabbitMQ VS Kafka VS Redis)](docs/01_MQ_CHOICE.md)
- [Snowflake Algorithm으로 URL 충돌률 1.3% → 0%, 생성속도 3.2배 개선](docs/05_SNOWFLAKE_ALGORITHM.md)
