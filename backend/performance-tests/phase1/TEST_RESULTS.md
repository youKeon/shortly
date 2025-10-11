# Phase 1 테스트 결과

## 테스트 결과

| 지표 | 값 |
|------|-----|
| **TPS** | **5,280** |
| **P95** | 177ms |
| **P90** | 113ms |
| **평균** | 64ms |
| **에러율** | 0% |
| **총 요청** | 2,220,229개 (7분) |

---
## 구현 사항
### 아키텍처

Spring Boot + JPA + MySQL (기본 구현)

---

### 설정

```yaml
hikari:
  maximum-pool-size: 50
  minimum-idle: 10

tomcat:
  threads:
    max: 500

# JDBC Batch 없음
```
