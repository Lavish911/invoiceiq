# InvoiceIQ

> **Production-deployed, multi-tenant invoice automation platform** built with Java 21, Spring Boot, PostgreSQL, Python FastAPI/Tesseract OCR, JobRunr, AWS S3, Docker, and Next.js.

InvoiceIQ turns invoice documents into structured financial data and routes them through a secure invoice lifecycle: **upload → asynchronous OCR → extraction → validation → review → approval**.

## 🚀 Live Demo

**Frontend:** https://frontend-production-044d3.up.railway.app

**Backend API:** https://backend-production-3d1d.up.railway.app

> The live application is deployed on Railway. The frontend proxies `/api/*` requests to the production Spring Boot backend. OCR runs as a private Railway service and documents are stored in a private AWS S3 bucket.

## ✨ What It Does

- Multi-tenant invoice management with strict tenant isolation
- JWT authentication with rotating refresh tokens
- Role-based access control: `ADMIN`, `APPROVER`, `SUBMITTER`
- Invoice CRUD with server-computed financial totals
- Secure PDF/image document upload
- Asynchronous OCR processing using JobRunr + FastAPI + Tesseract
- Deterministic extraction of invoice number, vendor, dates, currency, subtotal, tax, and total
- Approval workflow with rule-based routing and automatic approval when no rule matches
- Optimistic locking and idempotency for concurrency-safe operations
- Audit trail for invoice/workflow state transitions
- Durable private document storage with AWS S3
- Production frontend deployed with Next.js standalone Docker runtime

## 🏗️ Architecture

```text
                    ┌─────────────────────────┐
                    │      User Browser       │
                    │   Next.js Frontend      │
                    └────────────┬────────────┘
                                 │ HTTPS
                                 ▼
                    ┌─────────────────────────┐
                    │ Railway Frontend        │
                    │ Next.js + API Rewrite   │
                    └────────────┬────────────┘
                                 │ /api/*
                                 ▼
                    ┌─────────────────────────┐
                    │ Railway Backend         │
                    │ Java 21 / Spring Boot   │
                    │ JWT / RBAC / REST       │
                    └──────┬──────────┬───────┘
                           │          │
                    JDBC / Flyway    JobRunr
                           │          │
                           ▼          ▼
                    ┌───────────┐  ┌────────────────┐
                    │PostgreSQL │  │ Private OCR     │
                    │           │  │ FastAPI         │
                    └───────────┘  │ Tesseract       │
                                   └───────┬────────┘
                                           │
                                           ▼
                                   ┌────────────────┐
                                   │ Private AWS S3 │
                                   │ Document Store │
                                   └────────────────┘
```

### Design

InvoiceIQ is a **modular monolith**, not a collection of unnecessary microservices.

The Spring Boot application owns authentication, authorization, tenancy, invoice state, persistence, workflow, auditing, storage coordination, and background-job orchestration.

The Python service is intentionally narrow: it receives a document, runs OCR with Tesseract, extracts supported fields, and returns a validated response contract.

JobRunr persists background jobs in PostgreSQL, allowing document extraction to run asynchronously without blocking the upload request.

## 🔐 Security & Reliability

### Authentication
- JWT access + refresh token architecture
- Refresh-token rotation with SHA-256 hashing at rest
- Access/refresh token type separation
- BCrypt password hashing
- Stateless Spring Security

### Multi-tenancy
- Tenant identity comes from verified JWT claims
- Database user lookup is tenant-scoped
- Tenant-aware repository queries
- Fail-closed Hibernate tenant filtering
- Cross-tenant access is rejected

### Financial correctness
- Java `BigDecimal`
- PostgreSQL `NUMERIC(19,4)`
- Server-computed invoice totals
- Explicit optimistic-lock/version checks
- HTTP `409 Conflict` for stale writes and concurrency conflicts

### Idempotency
Invoice creation supports `X-Idempotency-Key` with payload-hash validation:
- same key + same payload → idempotent replay
- same key + different payload → `409 Conflict`

### Upload security
- PDF/JPEG/PNG allowlist
- 10 MB upload limit
- basename sanitization
- storage containment checks
- asynchronous processing after transaction commit

## 🔄 Invoice Lifecycle

```text
DRAFT
  │
  ├── Upload document
  │      └── JobRunr → OCR → extraction → writeback
  │
  ▼
NEEDS_REVIEW
  │
  ├── Matching workflow rule
  │        ▼
  │   PENDING_APPROVAL
  │        │
  │        ├── Approve → APPROVED
  │        └── Reject  → REJECTED
  │
  └── No matching rule → APPROVED
```

## 🧪 Verification

The project was built and hardened through milestone-based testing rather than relying only on happy-path manual checks.

Verified areas include:

- Java integration and security tests
- Python OCR tests
- PostgreSQL/Flyway validation
- Tenant-isolation tests
- Duplicate and idempotency race tests
- Optimistic-locking concurrency tests
- Real OCR integration
- Production S3 upload/read/delete and restart durability
- Production browser upload using real `multipart/form-data`
- Production OCR extraction with exact financial values
- Production frontend → backend → JobRunr → OCR → S3 → PostgreSQL flow

### Production OCR acceptance

A real browser upload against the public frontend was processed through the deployed production stack and produced:

```text
Subtotal   2000.00
Tax         469.12
Total      2469.12
```

The extracted values were written back to the production invoice successfully.

## 🛠️ Technology Stack

| Layer | Technology |
|---|---|
| Frontend | Next.js 16, React 19, TypeScript |
| Backend | Java 21, Spring Boot 3.3.4 |
| Security | Spring Security, JWT, BCrypt |
| Persistence | PostgreSQL, Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Async Jobs | JobRunr |
| OCR Service | Python 3.11, FastAPI, Tesseract, PyMuPDF |
| Storage | AWS S3 |
| Containers | Docker, Docker Compose |
| Deployment | Railway + AWS |
| Testing | JUnit 5, MockMvc, Testcontainers, pytest, Vitest |

## 📁 Project Structure

```text
invoiceiq/
├── src/main/java/com/invoiceiq/
│   ├── auth/
│   ├── tenant/
│   ├── invoice/
│   ├── vendor/
│   ├── document/
│   ├── workflow/
│   ├── audit/
│   ├── notification/
│   └── common/
├── src/main/resources/db/migration/
├── python-ocr/
├── frontend/
├── docs/
├── Dockerfile
├── docker-compose.yml
└── README.md
```

## ▶️ Run Locally

### Full Docker stack

```bash
docker compose up -d --build
```

Backend:

```
http://localhost:8080
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

Frontend development server:

```
http://localhost:3000
```

See `docs/DEPLOYMENT.md` for environment configuration and deployment details.

## 📚 Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [API Reference](docs/API.md)
- [Deployment Guide](docs/DEPLOYMENT.md)
- [Engineering Decisions](docs/ENGINEERING_DECISIONS.md)
- [Demo Guide](docs/DEMO.md)

## ⚠️ Current Limitations

- OCR extraction is heuristic/regex-based rather than ML-trained document understanding.
- Line-item extraction is not yet part of the OCR pipeline.
- Notifications are persisted in-app records; SMTP/Slack delivery is not implemented.
- Redis is reserved for future caching/rate-limiting and is not used by current business logic.
- Audit immutability is enforced by application design (no mutating API), not a database-level immutable table.

## 🔮 Future Improvements

- ML/layout-aware document extraction
- Line-item extraction
- External notification delivery
- Rate limiting
- Approval SLA/escalation
- Expanded observability and CI/CD automation

## 👤 Author

**Lavish Rahangdale**

B.Tech — Artificial Intelligence

[GitHub](https://github.com/Lavish911)

---

**InvoiceIQ** demonstrates production-oriented backend engineering across authentication, multi-tenancy, concurrency, asynchronous processing, OCR integration, cloud storage, Docker, and cloud deployment.
