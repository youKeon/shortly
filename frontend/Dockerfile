# Stage 1: Build
FROM node:20-alpine AS builder

WORKDIR /app

# package.json과 package-lock.json 복사 (캐시 활용)
COPY package*.json ./

# 의존성 설치
RUN npm ci

# 소스 코드 복사
COPY . .

# 프로덕션 빌드
RUN npm run build

# Stage 2: Production
FROM nginx:alpine

# Nginx 설정 파일 복사
COPY frontend/nginx.conf /etc/nginx/nginx.conf

# 빌드된 정적 파일 복사
COPY --from=builder /app/dist /usr/share/nginx/html

# 포트 80 노출
EXPOSE 80

# Nginx 실행
CMD ["nginx", "-g", "daemon off;"]
