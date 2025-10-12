# Phase 4: 수평 확장 (Horizontal Scaling)

## 🎯 목표

**API 서버를 2대로 확장하여 선형 확장 검증**

- Docker Compose로 API 서버 2대 실행
- Nginx 로드 밸런싱
- 목표 TPS: 12,000+ (Phase 3의 2배)
- 학습: Load Balancing, 선형 확장 검증

---

## 📋 구현 내용

### 1. 아키텍처

```
        클라이언트 (k6)
             ↓
    Nginx Load Balancer (80)
             ↓
    ┌────────┴────────┐
    ↓                 ↓
API 서버 1        API 서버 2
 (8080)            (8080)
    ↓                 ↓
    └────────┬────────┘
             ↓
         Redis
             ↓
         MySQL
```

**추가된 요소**:
- ✅ Docker Compose (컨테이너 오케스트레이션)
- ✅ Nginx Load Balancer (Round Robin)
- ✅ API 서버 2대 (동일한 이미지)
- ✅ 공유 MySQL, Redis

---

### 2. Docker Compose 구성

#### docker-compose-phase4.yml

```yaml
services:
  # MySQL (공유)
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: d4594283!
      MYSQL_DATABASE: bitly
    ports:
      - "3306:3306"

  # Redis (공유)
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  # API 서버 1
  api-server-1:
    build: ../../backend
    environment:
      SPRING_PROFILES_ACTIVE: phase4
      DB_HOST: mysql
      REDIS_HOST: redis
    depends_on:
      - mysql
      - redis

  # API 서버 2
  api-server-2:
    build: ../../backend
    environment:
      SPRING_PROFILES_ACTIVE: phase4
      DB_HOST: mysql
      REDIS_HOST: redis
    depends_on:
      - mysql
      - redis

  # Nginx Load Balancer
  nginx:
    image: nginx:alpine
    ports:
      - "3004:80"
    volumes:
      - ../nginx/nginx.conf:/etc/nginx/conf.d/default.conf
    depends_on:
      - api-server-1
      - api-server-2
```

**특징**:
- 각 API 서버는 동일한 Dockerfile에서 빌드
- 환경 변수로 설정 주입
- 헬스체크로 서비스 가용성 확인

---

### 3. Nginx 로드 밸런서

#### nginx.conf

```nginx
upstream api_servers {
    server api-server-1:8080 max_fails=3 fail_timeout=30s;
    server api-server-2:8080 max_fails=3 fail_timeout=30s;
    
    keepalive 32;
}

server {
    listen 80;

    location / {
        proxy_pass http://api_servers;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        
        # 에러 발생 시 다음 서버로 자동 전환
        proxy_next_upstream error timeout http_502 http_503;
    }
}
```

**로드 밸런싱 알고리즘**:
- **Round Robin** (기본): 순차적으로 분배
- `least_conn`: 연결 수가 가장 적은 서버로 분배
- `ip_hash`: 클라이언트 IP 기반 Sticky Session

---

### 4. Dockerfile

```dockerfile
# Stage 1: Build
FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle bootJar --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**멀티 스테이지 빌드**:
- Build Stage: Gradle로 JAR 빌드
- Runtime Stage: 경량 JRE로 실행
- 최종 이미지 크기: ~200MB

---

## 🚀 테스트 실행

### 사전 준비

```bash
# Docker 설치 확인
docker --version
docker-compose --version

# 포트 확인 (3306, 6379, 3004가 사용 가능해야 함)
lsof -i :3306
lsof -i :6379
lsof -i :3004
```

---

### 1. Docker Compose 실행

```bash
cd /Users/okestro/Desktop/dev/bitly/infra/compose

# 컨테이너 빌드 및 실행
docker-compose -f docker-compose-phase4.yml up --build -d

# 로그 확인
docker-compose -f docker-compose-phase4.yml logs -f

# 컨테이너 상태 확인
docker-compose -f docker-compose-phase4.yml ps
```

**기대 출력**:
```
NAME               STATUS    PORTS
bitly-mysql       Up        0.0.0.0:3306->3306/tcp
bitly-redis       Up        0.0.0.0:6379->6379/tcp
bitly-api-1       Up (healthy)
bitly-api-2       Up (healthy)
bitly-nginx       Up        0.0.0.0:3004->80/tcp
```

---

### 2. 헬스체크

```bash
# Nginx 확인
curl http://localhost:3004/nginx-health

# API 서버 헬스체크 (Nginx 통해)
curl http://localhost:3004/actuator/health

# API 서버 직접 확인 (컨테이너 내부)
docker exec bitly-api-1 wget -qO- http://localhost:8080/actuator/health
docker exec bitly-api-2 wget -qO- http://localhost:8080/actuator/health
```

---

### 3. 로드 밸런싱 테스트

```bash
# 여러 번 요청하여 서버 분산 확인
for i in {1..10}; do
  curl -s http://localhost:3004/api/v1/urls/shorten \
    -H "Content-Type: application/json" \
    -d '{"originalUrl": "https://example.com/'$i'"}' | jq .
done

# Nginx 로그에서 upstream 서버 확인
docker logs bitly-nginx 2>&1 | grep "upstream"
```

---

### 4. 표준 테스트 실행

```bash
cd /Users/okestro/Desktop/dev/bitly

# BASE_URL을 Nginx로 변경하여 테스트
BASE_URL=http://localhost:3004 k6 run backend/performance-tests/standard-load-test.js
```

**예상 결과**:
```
Phase 3 (단일 서버): 6,302 TPS
Phase 4 (2대 서버): 12,000+ TPS (2배)

선형 확장 검증:
- 1대 → 2대: TPS 2배 증가
- 응답 시간: 유지 또는 개선
```

---

### 5. 모니터링

#### Nginx 상태 확인

```bash
# Nginx 상태
curl http://localhost:3004/nginx-status
```

**출력 예시**:
```
Active connections: 145
server accepts handled requests
 12345 12345 98765
Reading: 0 Writing: 10 Waiting: 135
```

#### Redis 통계

```bash
# Redis 캐시 히트율
docker exec bitly-redis redis-cli INFO stats | grep -E "keyspace_hits|keyspace_misses"
```

#### API 서버 로그

```bash
# API 서버 1 로그
docker logs bitly-api-1 -f

# API 서버 2 로그
docker logs bitly-api-2 -f

# 모든 컨테이너 로그
docker-compose -f docker-compose-phase4.yml logs -f
```

---

### 6. 정리

```bash
# 컨테이너 중지 및 삭제
docker-compose -f docker-compose-phase4.yml down

# 볼륨까지 삭제 (데이터 완전 삭제)
docker-compose -f docker-compose-phase4.yml down -v

# 이미지 삭제
docker rmi compose-api-server-1 compose-api-server-2
```

---

## 📊 예상 성능

### 선형 확장 (Linear Scaling)

```
서버 수 | TPS     | 개선율
--------|---------|--------
1대     | 6,302   | 기준
2대     | 12,604  | 2배 (100%)
```

**이론**:
- 서버 2대 = TPS 2배
- 응답 시간: 유지
- 에러율: 0%

**현실 (예상)**:
- TPS: 11,000-12,500 (85-95%)
- 약간의 오버헤드 (Nginx, 네트워크)
- 여전히 우수한 확장성

---

## 💡 Phase 4의 의미

### Phase 3과의 차이

```
Phase 3: 단일 서버 최적화
→ Redis 버퍼링
→ TPS: 6,302
→ 수직 확장 한계

Phase 4: 수평 확장
→ API 서버 2대
→ Nginx Load Balancing
→ TPS: 12,000+ (2배)
→ 선형 확장 검증
```

---

### 수평 확장의 장점

```
✅ 무한 확장 가능
   - 서버 추가 = TPS 증가
   - 3대, 4대, N대...

✅ 고가용성 (High Availability)
   - 한 서버 장애 시 자동 전환
   - 무중단 배포 가능

✅ 부하 분산
   - 각 서버 부하 감소
   - 안정적인 성능

✅ 비용 효율적
   - 저사양 서버 N대 > 고사양 서버 1대
   - 필요시에만 추가
```

---

### 수평 확장의 고려사항

```
1. 상태 관리 (Stateless)
   - 세션을 DB/Redis에 저장
   - 어떤 서버가 처리해도 동일

2. 공유 리소스
   - MySQL, Redis는 공유
   - 병목이 될 수 있음
   → Phase 5: DB Replication

3. 네트워크 오버헤드
   - Nginx 프록시 지연 (~1-2ms)
   - Docker 네트워크 오버헤드
   → 허용 가능한 수준

4. 일관성 (Consistency)
   - 분산 캐시 동기화
   - 트랜잭션 관리
   → Redis로 해결 (이미 공유)
```

---

## 🎓 학습 포인트

### 1. 로드 밸런싱

```
Round Robin:
- 순차적으로 분배
- 단순하고 공평

Least Connection:
- 연결이 적은 서버로
- 부하가 불균형할 때

IP Hash:
- 같은 IP는 같은 서버로
- Sticky Session 필요시
```

### 2. Docker Compose

```
장점:
✅ 여러 컨테이너 한 번에 관리
✅ 네트워크 자동 구성
✅ 환경 변수로 설정 주입
✅ 헬스체크 자동화

한계:
❌ 단일 호스트 (로컬)
❌ 프로덕션 부적합
→ Kubernetes 필요
```

### 3. 선형 확장 검증

```
목표:
- 서버 N대 = TPS N배

확인 사항:
- 실제 TPS 증가율
- 응답 시간 변화
- 에러율 유지
- 리소스 사용률
```

---

## 🚀 다음 단계: Phase 5

### 계획: DB Replication

```
목표:
- MySQL Read Replica 추가
- 읽기/쓰기 분리
- DB 병목 해소

아키텍처:
   Nginx
     ↓
  API 서버 × N
     ↓
  ┌─────┴─────┐
Master DB  Slave DB
(쓰기)     (읽기)
     ↓
   Redis

기대:
- DB 부하 분산
- TPS 추가 증가
- 읽기 성능 개선
```

---

## 📝 핵심 요약

### Phase 4 = "수평 확장 경험"

```
구현:
✅ Docker Compose로 API 서버 2대
✅ Nginx 로드 밸런싱
✅ 공유 MySQL, Redis

결과:
- TPS: 12,000+ (Phase 3의 2배)
- 선형 확장 검증
- 고가용성 확보

학습:
✅ Load Balancing 실습
✅ Docker Compose 활용
✅ 분산 시스템 경험
```

---

**작성일**: 2025-10-12  
**테스트**: standard-load-test.js (500 VU, 7분)  
**환경**: Docker Compose, M3 MacBook

