# Phase 1: 기본 구현 (Baseline)

## 목표

**Spring Boot + MySQL 성능 측정**

- 최소한의 설정
- 표준 테스트 스크립트 수행 가능한 기본 구성
- Phase 2, 3의 기준선(Baseline) 확립

## 구현 내용

### 1. 기본 아키텍처

```
사용자 요청
    ↓
Spring Boot (Tomcat)
    ↓
JPA (Hibernate)
    ↓
MySQL
```
### 2. 설정

#### HikariCP Connection Pool

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 3000
```

**선정 이유**:
- 500 VU 테스트에 필요한 최소 연결 수
- 리디렉션 90%는 단순 조회

#### Tomcat Thread Pool

```yaml
server:
  tomcat:
    threads:
      max: 500
```

**선정 이유**:
- 표준 테스트: 최대 500 VU
- 1 VU당 1 스레드 필요
