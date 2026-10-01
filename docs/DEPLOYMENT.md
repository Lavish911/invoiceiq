# InvoiceIQ — Deployment Guide

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Prerequisites](#prerequisites)
- [Quick Start (Docker Compose)](#quick-start-docker-compose)
- [Local Development](#local-development)
- [Configuration Reference](#configuration-reference)
- [Storage Configuration](#storage-configuration)
- [Production Deployment](#production-deployment)
- [Health Checks](#health-checks)
- [Troubleshooting](#troubleshooting)

---

## Architecture Overview

InvoiceIQ is a modular monolith consisting of:

| Component | Technology | Port |
|-----------|-----------|------|
| **Backend API** | Java 21 / Spring Boot 3.3.4 | 8080 |
| **OCR Service** | Python 3.11 / FastAPI + Tesseract | 8000 |
| **Database** | PostgreSQL 15 | 5432 |
| **Cache** | Redis 7 (reserved, not used by business logic) | 6379 |
| **Job Scheduler** | JobRunr (SQL-backed, embedded in backend) | — |

```
┌──────────────────────────────────────────────────┐
│                  Docker Compose                  │
│                                                  │
│  ┌──────────┐    ┌──────────┐    ┌───────────┐  │
│  │ Backend  │───▶│  Python  │    │ PostgreSQL│  │
│  │ (8080)   │    │  OCR     │    │  (5432)   │  │
│  │          │───▶│  (8000)  │    │           │  │
│  └──────────┘    └──────────┘    └───────────┘  │
│       │                               ▲         │
│       └───────────────────────────────┘         │
│                                                  │
│  ┌──────────┐                                    │
│  │  Redis   │ (reserved)                         │
│  │  (6379)  │                                    │
│  └──────────┘                                    │
└──────────────────────────────────────────────────┘
```

---

## Prerequisites

- **Docker** 20.10+ and **Docker Compose** v2
- (For local dev) Java 21, Maven 3.9+, Python 3.11+

---

## Quick Start (Docker Compose)

```bash
# 1. Clone the repository
git clone https://github.com/Lavish911/invoiceiq.git
cd invoiceiq

# 2. Create environment file
cp .env.example .env
# Edit .env — at minimum set JWT_SECRET_KEY:
#   openssl rand -base64 32

# 3. Start all services
docker compose up -d --build

# 4. Verify health
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

All four services (postgres, redis, python-ocr, backend) start with health checks.
The backend waits for postgres, redis, and python-ocr to be healthy before starting.

---

## Local Development

For iterating on the Java backend without rebuilding Docker images:

```bash
# 1. Start infrastructure only
docker compose up -d postgres redis python-ocr

# 2. Set environment variables
# PowerShell:
$env:JWT_SECRET_KEY="$(openssl rand -base64 32)"
# Bash:
export JWT_SECRET_KEY=$(openssl rand -base64 32)

# 3. Run Spring Boot
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 4. Run tests
./mvnw test -Dspring.profiles.active=test
```

### Python OCR Local Dev

```bash
cd python-ocr
python -m venv venv
source venv/bin/activate  # or .\venv\Scripts\activate on Windows
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

---

## Configuration Reference

All configuration is driven by environment variables. See `.env.example` for the complete list.

### Required Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `JWT_SECRET_KEY` | HS256 signing key (base64, ≥256 bits) | Output of `openssl rand -base64 32` |
| `AI_SERVICE_TOKEN` | Shared auth token between backend and OCR | Any random string |

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_USER` | `invoiceiq` | PostgreSQL username |
| `POSTGRES_PASSWORD` | `invoiceiq_password` | PostgreSQL password |
| `POSTGRES_DB` | `invoiceiq_db` | Database name |
| `POSTGRES_URL` | `jdbc:postgresql://postgres:5432/invoiceiq_db` | JDBC connection URL (docker profile) |

### OCR Service

| Variable | Default | Description |
|----------|---------|-------------|
| `AI_SERVICE_URL` | `http://python-ocr:8000` | OCR service base URL |
| `OCR_CONNECT_TIMEOUT` | `5` | Connection timeout (seconds) |
| `OCR_READ_TIMEOUT` | `120` | Read timeout (seconds) |

### Storage

| Variable | Default | Description |
|----------|---------|-------------|
| `STORAGE_TYPE` | `local` | `local` or `s3` |
| `STORAGE_LOCAL_DIR` | `./data/storage` | Local filesystem path |
| `S3_BUCKET` | — | S3 bucket name |
| `S3_ENDPOINT` | — | S3 endpoint URL (for MinIO/compatible) |
| `S3_REGION` | `us-east-1` | AWS region |
| `S3_ACCESS_KEY` | — | AWS access key |
| `S3_SECRET_KEY` | — | AWS secret key |

---

## Storage Configuration

### Local Filesystem (Default)

No additional configuration needed. Files are stored in `./data/storage` (or the configured `STORAGE_LOCAL_DIR`).

**Limitation:** Not suitable for multi-instance deployments or horizontal scaling. Files are stored on the local disk of whichever host runs the container.

### S3 / S3-Compatible (Production)

Set `STORAGE_TYPE=s3` and configure the S3 variables:

```env
STORAGE_TYPE=s3
S3_BUCKET=invoiceiq-documents
S3_REGION=us-east-1
S3_ACCESS_KEY=AKIA...
S3_SECRET_KEY=...
```

For **MinIO** or other S3-compatible storage:

```env
S3_ENDPOINT=http://minio:9000
```

---

## Production Deployment

### Pre-deployment Checklist

- [ ] Set a strong, random `JWT_SECRET_KEY` (≥256 bits)
- [ ] Set a strong, random `AI_SERVICE_TOKEN`
- [ ] Change `POSTGRES_PASSWORD` from the default
- [ ] Configure `STORAGE_TYPE=s3` for durable document storage
- [ ] Set `management.endpoint.health.show-details=never` (default)
- [ ] Place behind a reverse proxy (nginx/Caddy) with TLS
- [ ] Configure CORS allowed origins in `SecurityConfig` for your domain

### Spring Profiles

| Profile | Purpose |
|---------|---------|
| `local` | Local development against localhost services |
| `docker` | Docker Compose deployment (service hostnames) |
| `test` | Automated tests (H2 in-memory database) |

### Reverse Proxy (nginx example)

```nginx
server {
    listen 443 ssl;
    server_name invoiceiq.example.com;

    ssl_certificate     /etc/ssl/certs/invoiceiq.pem;
    ssl_certificate_key /etc/ssl/private/invoiceiq.key;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # For file uploads (OCR)
        client_max_body_size 50M;
    }
}
```

### Database Migrations

Flyway runs automatically on application startup. Migrations are located in `src/main/resources/db/migration/`.

To see current migration status:
```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

### Backup

```bash
# PostgreSQL backup
docker exec invoiceiq-postgres pg_dump -U invoiceiq invoiceiq_db > backup.sql

# Restore
cat backup.sql | docker exec -i invoiceiq-postgres psql -U invoiceiq invoiceiq_db
```

---

## Health Checks

| Endpoint | Description |
|----------|-------------|
| `GET /actuator/health` | Spring Boot health (UP/DOWN) |
| `GET /actuator/info` | Application info |
| `GET /actuator/metrics` | Metrics (JVM, HTTP, DB pool) |
| `GET http://ocr:8000/health` | Python OCR health |

Docker Compose health checks are configured for all services with automatic restarts.

---

## Troubleshooting

### Backend won't start

1. Check PostgreSQL is healthy: `docker compose ps`
2. Verify `JWT_SECRET_KEY` is set
3. Check logs: `docker compose logs backend`

### OCR extraction fails

1. Verify OCR service is healthy: `curl http://localhost:8000/health`
2. Check `AI_SERVICE_TOKEN` matches between backend and OCR
3. Check timeout settings (`OCR_READ_TIMEOUT`) for large documents

### Flyway migration errors

1. Check for schema conflicts: `docker compose logs backend | grep -i flyway`
2. If stuck, verify no partial migrations in `flyway_schema_history`

### File upload errors

1. Verify storage directory exists and is writable
2. For S3: verify credentials and bucket permissions
3. Check `client_max_body_size` if behind nginx
