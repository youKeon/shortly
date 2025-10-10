## 최종 목표

### 핵심 목표
- 10,000 TPS 달성 (현재 대비 185배)

### 부가 목표
- 동시 사용자: 2,000명 이상 처리
- P99 응답시간: 50ms 이하
- 실패율: 0.1% 이하

## 단계별 마일스톤

### Phase 1: DB 최적화

목표

- TPS: 1,000 (18.5배)

작업 내용
- 인덱스 추가
- EXPLAIN 확인
- Connection Pool, Tomcat 튜닝

테스트 스크립트

```jsx
// stress-phase1.js
export const options = {
  stages: [
    { duration: '2m', target: 100 },
    { duration: '5m', target: 200 },  // Phase 1 목표
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    'http_req_duration': ['p(95)<100'],
    'http_req_failed': ['rate<0.05'],
  },
};
```

성공 기준

- TPS ≥ 1,000
- 실패율 < 5%
- P95 < 100ms
- DB 쿼리 시간 < 10ms

### Phase 2: Redis 캐싱

목표

- TPS: 3,000 (55배)
- VUs: 500명

작업 내용

- Redis 캐싱
- 캐싱 전략 구현
- Connection Pool, Tomcat 튜닝

테스트 스크립트

```jsx
// stress-phase2.js
export const options = {
  stages: [
    { duration: '2m', target: 200 },
    { duration: '5m', target: 500 },  // Phase 2 목표
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    'http_req_duration': ['p(95)<50'],
    'http_req_failed': ['rate<0.01'],
  },
};
```

성공 기준

- TPS ≥ 3,000
- 실패율 < 1%
- P95 < 50ms

### Phase 3: Kafka 비동기 처리

목표

- TPS: 5,000 (92배)
- VUs: 1,000명

작업 내용

- Kafka 도입
- 클릭 이벤트 비동기 처리
- Connection Pool, Tomcat 튜닝

테스트 스크립트

```jsx
// stress-phase3.js
export const options = {
  stages: [
    { duration: '2m', target: 500 },
    { duration: '5m', target: 1000 },  // Phase 3 목표
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    'http_req_duration': ['p(95)<30'],
    'http_req_failed': ['rate<0.005'],
  },
};
```

성공 기준

- TPS ≥ 5,000
- 실패율 < 0.5%
- P95 < 30ms
- P99 < 50ms
- 클릭 이벤트 유실 < 0.1%

### Phase 4: 스케일 아웃

목표

- TPS: 10,000 (185배) ← 최종 목표!
- VUs: 2,000명

작업 내용

- Connection Pool 확장
- Redis 클러스터링
- 애플리케이션 스케일 아웃
- DB Read Replica

테스트 스크립트

```jsx
// stress-phase4-final.js
export const options = {
  stages: [
    { duration: '2m', target: 1000 },
    { duration: '5m', target: 2000 },  // 최종 목표!
    { duration: '10m', target: 2000 }, // 안정성 확인
    { duration: '2m', target: 0 },
  ],
  thresholds: {
    'http_req_duration': ['p(95)<30', 'p(99)<50'],
    'http_req_failed': ['rate<0.001'],
  },
};
```

성공 기준

- TPS ≥ 10,000
- 실패율 < 0.1%
- P95 < 30ms
- P99 < 50ms
- 10분간 안정 운영
