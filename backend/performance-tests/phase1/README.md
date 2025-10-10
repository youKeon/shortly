# Phase 1: DB 최적화

## 목표
- **TPS**: 1,000 (현재 대비 18.5배)
- **동시 사용자**: 200명
- **P95 응답시간**: < 100ms
- **실패율**: < 5%

## 최적화 내용

### 1. 데이터베이스 인덱스 추가

#### 문제점
- `url_clicks` 테이블의 `url_id` 컬럼에 인덱스 없음
- 매 리디렉션마다 레코드 전체 스캔 (type: ALL)
- `url_clicks` 테이블의 `url_id` 컬럼에 인덱스 추가

### 2. HikariCP Connection Pool 튜닝

#### 최적화 설정
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50        # 200 VUs 동시 처리
```

#### 선정 이유
- 200 VUs 처리를 위한 충분한 커넥션 수
- MySQL 기본 max_connections(151) 범위 내
- 나머지 설정은 HikariCP 기본값 사용 (검증된 값)

### 3. Tomcat 서버 튜닝

#### 최적화 설정
```yaml
server:
  tomcat:
    threads:
      max: 200              # 200 VUs 동시 처리
```

#### 선정 이유
- 200 VUs 처리를 위한 충분한 스레드 수
- 나머지 설정은 Tomcat 기본값 사용 (검증된 값)

### 4. JPA 배치 처리 최적화

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate          # 스키마 변경 방지
    show-sql: false               # 로그 최소화
    properties:
      hibernate:
        jdbc.batch_size: 50       # DB 왕복 횟수 감소
        order_inserts: true       # 배치 효율 향상
        order_updates: true       # 배치 효율 향상
```
