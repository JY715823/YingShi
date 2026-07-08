# R1-M4: HTTPS 部署加固 实施文档

## 一、目标

提供 Nginx HTTPS 反向代理配置模板和生产环境 docker-compose 文件，确保部署时可直接使用。添加安全头（HSTS、X-Frame-Options 等）。

## 二、涉及文件清单

| 文件 | 端 | 改动类型 | 说明 |
|------|-----|---------|------|
| `docs/deployment/nginx-https.conf` | 部署 | 新增 | Nginx HTTPS 配置模板 |
| `docker-compose.prod.yml` | 部署 | 新增 | 生产环境 docker-compose（含 Nginx） |
| `README.md` | 文档 | 修改 | 添加 HTTPS 部署说明章节 |

## 三、配置级实施方案

### 3.1 nginx-https.conf

```nginx
# YingShi Server — Nginx HTTPS Reverse Proxy
# Place this in /etc/nginx/conf.d/ or /etc/nginx/sites-enabled/
# Obtain SSL cert: certbot certonly --nginx -d api.yingshi.example.com

# HTTP → HTTPS redirect
server {
    listen 80;
    listen [::]:80;
    server_name api.yingshi.example.com;
    return 301 https://$host$request_uri;
}

# HTTPS server
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name api.yingshi.example.com;

    # SSL certificates (Let's Encrypt or cloud provider)
    ssl_certificate     /etc/letsencrypt/live/api.yingshi.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.yingshi.example.com/privkey.pem;

    # SSL hardening
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;
    ssl_session_cache shared:SSL:10m;
    ssl_session_timeout 1d;
    ssl_session_tickets off;

    # Security headers
    add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;

    # File upload size (match Spring Boot multipart config)
    client_max_body_size 550M;

    # Proxy to Spring Boot
    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Connection "";
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }

    # Actuator health (restrict to internal)
    location /actuator/ {
        allow 127.0.0.1;
        deny all;
        proxy_pass http://127.0.0.1:8080;
    }
}
```

**关键设计**：
- `X-Forwarded-For $remote_addr`（非 `$proxy_add_x_forwarded_for`）— 防止 IP 伪造（对应 P1-8）
- `client_max_body_size 550M` — 匹配 Spring Boot 的 `max-request-size: 550MB`
- `/actuator/` 仅允许 localhost — 防止健康检查信息泄露

### 3.2 docker-compose.prod.yml

```yaml
# Production docker-compose with Nginx reverse proxy
# Usage: docker compose -f docker-compose.prod.yml up -d
# Prerequisite: Place SSL certs in ./nginx/certs/

services:
  nginx:
    image: nginx:1.27-alpine
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./docs/deployment/nginx-https.conf:/etc/nginx/conf.d/default.conf:ro
      - ./nginx/certs:/etc/letsencrypt:ro
    depends_on:
      server:
        condition: service_healthy

  # Include base services from docker-compose.yml
  postgres:
    extends:
      file: docker-compose.yml
      service: postgres

  minio:
    extends:
      file: docker-compose.yml
      service: minio

  minio-init:
    extends:
      file: docker-compose.yml
      service: minio-init

  server:
    extends:
      file: docker-compose.yml
      service: server
    ports:
      - "127.0.0.1:${SERVER_HOST_PORT:-8080}:8080"
```

### 3.3 README.md — 添加部署章节

在项目 README 末尾添加 HTTPS 部署说明，指向 `docs/deployment/nginx-https.conf` 和 `docker-compose.prod.yml`，包含证书获取和续期流程。

## 四、验收标准

- [ ] Nginx 配置模板包含 HSTS、X-Frame-Options、X-Content-Type-Options 安全头
- [ ] `X-Forwarded-For` 使用 `$remote_addr`（防伪造）
- [ ] docker-compose.prod.yml 可正常启动（nginx + server + postgres + minio）
- [ ] Actuator 端点仅 localhost 可访问
