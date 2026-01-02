FROM node:21-alpine AS frontend-build

WORKDIR /app

# Copy frontend source
COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# Production stage
FROM nginx:alpine

# Copy frontend build
COPY --from=frontend-build /app/dist /usr/share/nginx/html

# Copy nginx config
COPY infra/nginx/nginx.conf /etc/nginx/nginx.conf

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
