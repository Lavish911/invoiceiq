# Resume Project Summary: InvoiceIQ

## A. One-line description

Multi-tenant invoice automation platform (Java Spring Boot + FastAPI/Tesseract OCR) with JWT/RBAC, rule-driven approval workflows, and concurrency-safe persistence.

## B. Resume bullets

- Built a modular monolith in **Java 21 / Spring Boot 3** with shared-schema multi-tenancy (JWT-derived tenant context, scoped queries, fail-closed Hibernate filter) and **JWT + rotating refresh-token** auth under **ADMIN/APPROVER/SUBMITTER RBAC**.
- Implemented an async document pipeline: validated upload → tenant-isolated storage → **JobRunr** worker → **FastAPI/Tesseract** OCR service with deterministic Decimal extraction, persisted with bounded retries and crash-safe processing leases.
- Delivered a rule-based approval engine with sequential steps, `@Version` optimistic locking plus explicit version checks, and idempotency keys backed by unique indexes — concurrent approve/approve and approve/reject races covered by `CountDownLatch` integration tests asserting exactly one winner and HTTP 409 for losers.
- Established migration discipline with **Flyway (V1–V6)** validated against Testcontainers PostgreSQL, and a 48-test Java suite (MockMvc, H2, Testcontainers) plus a 6-test Python suite, all green with zero skips.
- Recorded transactional audit events for every workflow transition with a tenant-scoped read API, using exact-decimal (`BigDecimal`/`NUMERIC(19,4)`) money handling throughout.

## C. 30-second explanation

InvoiceIQ digitizes invoice approvals for multi-tenant B2B use. Uploads go through OCR in the background, invoices carry server-computed totals, and a rule engine routes them through sequential approvals with full audit history — one tenant can never see another's data.

## D. 60-second explanation

The core is a Java 21 Spring Boot backend on PostgreSQL. Multi-tenancy is shared-schema: the JWT filter verifies the user against their tenant row, binds the tenant to a request context, and every repository call is tenant-scoped, with a Hibernate filter as a fail-closed backstop. Uploads are validated and handed to JobRunr, which calls a small FastAPI service running Tesseract; results come back as decimal strings into `BigDecimal` columns. Approval uses version-checked steps so concurrent approvers get exactly one winner and one 409.

## E. 2-minute technical explanation

Three hard problems shaped the design. **Isolation**: rather than trusting developers to pass tenant IDs, identity flows from verified JWT claims into a `ThreadLocal` that is always cleared, repository methods are tenant-scoped by signature, and an aspect enforces the filter — cross-tenant access is covered by negative tests asserting 404s. **Money and duplicates**: totals are derived server-side from line items, duplicates are blocked by composite unique constraints (not just application checks), and idempotency keys carry a payload hash so retries replay but conflicting reuse fails loudly. **Concurrency**: invoice updates and approval steps combine JPA `@Version` with explicit expected-version checks, both mapped to 409; I proved the race behavior with multi-threaded latch tests rather than assuming the annotation suffices. OCR extraction itself is deliberately heuristic (regex over Tesseract text with fixed confidence weights) — honest, deterministic, and tested — not ML. JobRunr jobs persist in Postgres, not Redis; Redis is reserved future infrastructure, and I can explain exactly why the extra moving part wasn't warranted yet.

## F. Technologies

- **Languages:** Java 21, Python 3.11, SQL
- **Frameworks:** Spring Boot 3.3.4, Spring Data JPA, Spring Security, FastAPI
- **Data:** PostgreSQL 15, Flyway, H2 (fast tests), Redis 7 (in Compose, currently unused)
- **Libraries:** Tesseract (via pytesseract), PyMuPDF, Pillow, JobRunr, jjwt, Maven
- **Infra/testing:** Docker, Docker Compose, Testcontainers, JUnit 5, MockMvc, pytest

## G. Interview questions (all answerable from this repo)

1. Why a modular monolith — what would force you to split it?
2. Why is the OCR service a separate Python process instead of a Java library?
3. Walk through tenant identity from HTTP request to SQL predicate; where could it leak?
4. Why require `tenantId` at login instead of deriving it from email alone?
5. How do idempotency keys differ from the business duplicate constraint, and why have both?
6. What happens, mechanically, when two approvers race — which 409 fires first?
7. Why does JobRunr use Postgres here instead of Redis, and when would you switch?
8. How does refresh rotation limit the damage of a stolen token?
9. Why `BigDecimal(String)` and not `double` anywhere near money?
10. What guarantees an audit row exists exactly when a state change commits?
11. Why Testcontainers for migrations but H2 for most tests — what does each prove?
12. How do Flyway versions stay consistent with JPA entities?
13. What breaks if the Python service is down mid-extraction, and how does the system recover?
14. Why local filesystem storage, and what is the exact migration path to S3?
15. How does a workflow conflict become an HTTP 409 — trace the exception path.
16. What does the 5-minute processing lease protect against, and what doesn't it cover?
17. Why is there no `totalAmount` field on the create-invoice request?
