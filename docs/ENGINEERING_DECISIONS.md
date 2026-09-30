# Engineering Decisions

Each entry states the decision as implemented, why it fits this project, and its trade-off. "Deferred" marks explicitly out-of-scope work, not missing implementation.

## 1. Modular monolith

- **Decision**: One Spring Boot deployment for auth, tenancy, invoices, documents, workflow, audit, notifications.
- **Reasoning**: Single deployable, single database, and natural `@Transactional` boundaries (invoice + audit + notification commit together) without distributed transactions.
- **Trade-off**: Must be split later if teams or scale demand independent deploys. Deferred: service decomposition.

## 2. Python sidecar for OCR

- **Decision**: Tesseract OCR behind a small FastAPI service instead of in-JVM.
- **Reasoning**: Best OCR ecosystem, independent CPU scaling, crash isolation from the API process.
- **Trade-off**: HTTP boundary needs timeouts, auth, and contract tests (all present); operational footprint of a second runtime.

## 3. BigDecimal / NUMERIC(19,4) for money

- **Decision**: `BigDecimal` in Java, `NUMERIC(19,4)` in Postgres; OCR emits decimal strings parsed as `BigDecimal(String)`; totals computed server-side, never trusted from clients.
- **Reasoning**: Exact decimal arithmetic for financial data.
- **Trade-off**: Verbose arithmetic and explicit scale/rounding choices.

## 4. JWT with rotating refresh tokens

- **Decision**: Short-lived access JWTs plus persisted, hashed, single-use refresh tokens with `token_type` separation.
- **Reasoning**: Stateless API scaling with recoverable sessions; rotation bounds the blast radius of a stolen token.
- **Trade-off**: Refresh-token table needs lifecycle management (revocation + expiry purge).

## 5. Shared-schema tenant isolation

- **Decision**: `tenant_id` on every tenant row; identity from verified JWT + DB row match; scoped repository methods as the primary guard; Hibernate filter as fail-closed second layer.
- **Reasoning**: Cheapest correct model for many small tenants; keeps joins and transactions simple.
- **Trade-off**: No database-level composite FKs — isolation rests on code discipline, covered by cross-tenant negative tests.

## 6. Optimistic locking + explicit version checks

- **Decision**: JPA `@Version` everywhere it matters, plus client-supplied expected versions on approval, both mapped to HTTP 409.
- **Reasoning**: No locks held across user think-time; races collapse to deterministic conflicts.
- **Trade-off**: Clients must handle 409 retries; version fields travel in DTOs.

## 7. Idempotency via key + payload hash + unique index

- **Decision**: `X-Idempotency-Key` with SHA-style payload comparison backed by a partial unique index, separate from business duplicate protection.
- **Reasoning**: Survives retries *and* concurrent duplicate submits; different-payload reuse is a loud 409, not silent acceptance.
- **Trade-off**: Clients must send stable keys; key storage grows (purge policy deferred).

## 8. Flyway for schema, validate everywhere real

- **Decision**: Versioned SQL migrations (V1–V6); `ddl-auto: validate` on real profiles; a Testcontainers test migrates fresh Postgres and validates the JPA model against it.
- **Reasoning**: Deterministic schema in every environment.
- **Trade-off**: H2 `create-drop` still backs fast unit tests, so H2-only runs prove less than the Postgres run — the PG test is the gate that matters.

## 9. JobRunr on PostgreSQL, Redis reserved

- **Decision**: JobRunr with Spring Boot auto-configured **PostgreSQL** storage; Redis ships in Compose but is intentionally unwired.
- **Reasoning**: One fewer stateful dependency to operate correctly; jobs already need transactional proximity to Postgres rows.
- **Trade-off**: Throughput ceiling of DB polling; wire Redis (or another broker) when job volume demands it.

## 10. Transactional audit trail

- **Decision**: Audit rows written in the same transaction as state changes; no update/delete API for audit data.
- **Reasoning**: Action and evidence can never diverge; simple to reason about.
- **Trade-off**: Immutability is conventional, not constraint-enforced; high-volume tenants will need partitioning/retention.

## 11. Synchronous notification records, deferred dispatch

- **Decision**: Persist `IN_APP` notification rows synchronously after commit with simulated (logged) delivery; failures never roll back decisions.
- **Reasoning**: Decouples correctness (persisted intent) from delivery latency/flakiness.
- **Trade-off**: No external channel exists yet — SMTP/Slack dispatch is explicit future work.

## 12. Local filesystem storage

- **Decision**: Tenant-scoped directories with basename sanitization and read/write containment checks.
- **Reasoning**: Zero infrastructure for V1; traversal-tested.
- **Trade-off**: Single-node only; S3/MinIO migration is the known next step.
