import type { Phase, PhaseConfig } from '../types';

export const phaseConfig: Record<Phase, PhaseConfig> = {
  phase1: {
    name: 'Phase 1: DB 최적화',
    description: '데이터베이스 인덱스 추가 및 Connection Pool 튜닝으로 기본 성능 향상',
    goals: [
      { label: 'TPS', value: '≥ 1,000' },
      { label: '동시 사용자', value: '200 VUs' },
      { label: 'P95 응답시간', value: '< 100ms' },
      { label: '실패율', value: '< 5%' },
    ],
    optimizations: [
      {
        title: 'DB 인덱스 추가',
        description: 'url_clicks 테이블에 url_id 인덱스를 추가하여 풀스캔 제거',
        code: 'CREATE INDEX idx_url_clicks_url_id ON url_clicks(url_id);',
      },
      {
        title: 'HikariCP Connection Pool 튜닝',
        description: '200 VUs 처리를 위한 커넥션 풀 크기 증가',
        code: 'hikari:\n  maximum-pool-size: 50',
      },
      {
        title: 'JPA 배치 처리',
        description: 'DB 왕복 횟수를 50배 감소시키는 배치 처리 활성화',
        code: 'hibernate:\n  jdbc.batch_size: 50\n  order_inserts: true\n  order_updates: true',
      },
      {
        title: 'Tomcat 스레드 튜닝',
        description: '200 VUs 동시 처리를 위한 스레드 풀 증가',
        code: 'tomcat:\n  threads:\n    max: 200',
      },
    ],
  },
  phase2: {
    name: 'Phase 2: Redis 캐싱',
    description: 'Redis 캐시 도입으로 읽기 성능 3배 향상',
    goals: [
      { label: 'TPS', value: '≥ 3,000' },
      { label: '동시 사용자', value: '500 VUs' },
      { label: 'P95 응답시간', value: '< 50ms' },
      { label: '실패율', value: '< 1%' },
    ],
    optimizations: [
      {
        title: 'Redis 캐시 도입',
        description: 'URL 조회 결과를 Redis에 캐싱하여 DB 부하 감소',
      },
      {
        title: 'Cache-Aside 패턴',
        description: '캐시 미스 시 DB 조회 후 캐싱',
      },
      {
        title: 'Connection Pool 증가',
        description: '높은 TPS를 위한 커넥션 풀 확장',
        code: 'hikari:\n  maximum-pool-size: 100',
      },
    ],
  },
  phase3: {
    name: 'Phase 3: Kafka 비동기 처리',
    description: '클릭 이벤트 비동기 처리로 응답 시간 단축',
    goals: [
      { label: 'TPS', value: '≥ 5,000' },
      { label: '동시 사용자', value: '1,000 VUs' },
      { label: 'P95 응답시간', value: '< 30ms' },
      { label: '실패율', value: '< 0.5%' },
    ],
    optimizations: [
      {
        title: 'Kafka 이벤트 처리',
        description: '클릭 기록을 Kafka로 비동기 처리',
      },
      {
        title: '응답 시간 개선',
        description: 'INSERT 작업을 비동기로 전환하여 즉시 응답',
      },
      {
        title: '이벤트 손실 방지',
        description: 'Kafka 메시지 보장으로 데이터 무결성 유지',
      },
    ],
  },
  phase4: {
    name: 'Phase 4: 스케일 아웃',
    description: '수평 확장으로 최종 목표 달성',
    goals: [
      { label: 'TPS', value: '≥ 10,000' },
      { label: '동시 사용자', value: '2,000 VUs' },
      { label: 'P95 응답시간', value: '< 30ms' },
      { label: '실패율', value: '< 0.1%' },
    ],
    optimizations: [
      {
        title: 'Redis 클러스터링',
        description: 'Redis 3대 클러스터로 캐시 성능 향상',
      },
      {
        title: '애플리케이션 스케일 아웃',
        description: 'Spring Boot 애플리케이션 3대로 확장',
      },
      {
        title: 'DB Read Replica',
        description: '읽기 전용 복제본으로 DB 부하 분산',
      },
      {
        title: '로드 밸런서',
        description: 'Nginx로 트래픽 분산 처리',
      },
    ],
  },
};

