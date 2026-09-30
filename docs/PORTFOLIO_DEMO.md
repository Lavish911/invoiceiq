# Portfolio Demo Plan

Capture plan only — no screenshots are fabricated here. Record each item against a locally running stack (`docker-compose up -d`, backend on `:8080`).

## 1. Architecture diagram

Render the README Mermaid diagram (backend → Postgres, JobRunr → FastAPI → Tesseract). One image.

## 2. Swagger UI

`http://localhost:8080/swagger-ui.html` — expanded invoice + workflow sections. One screenshot proving the API surface exists.

## 3. Login / tokens

[literal `POST /api/auth/login` request/response in Postman or curl, with `accessToken`/`refreshToken` keys visible and secrets redacted. Shows tenant-scoped login fields (`email`, `password`, `tenantId`).

## 4. Invoice creation + idempotency

Two side-by-side responses: `201 Created` on first `POST /api/invoices`, `200 OK` with the identical id on replayed `X-Idempotency-Key`. Shows server-computed `totalAmount`.

## 5. Document upload + extraction

`202 Accepted` upload response, then the `GET …/extraction` result transitioning `IN_PROGRESS → COMPLETED` with `extracted_data` and confidence scores. Redact nothing structural; tenant IDs may be trimmed.

## 6. NEEDS_REVIEW → approval workflow

`POST …/submit` (200), `POST …/workflow/initiate` (200), approver `POST …/approval/approve` (200) — three panels or one terminal transcript. Optionally a second terminal showing the racing request receiving `409`.

## 7. Audit trail

`GET /api/invoices/{id}/audit` response showing `SUBMITTED → WORKFLOW_CREATED → APPROVED` with actor IDs and timestamps.

## 8. Test results

Terminal output of `./mvnw.cmd clean verify` tail (`Tests run: 48, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`) plus `pytest` tail (`6 passed`). One screenshot; do not edit the numbers — re-run live if challenged.

## 9. What NOT to screenshot

Real user emails, production secrets, the local `data/` directory contents, Docker daemon credentials visible in earlier debug logs (`authConfig` username in Testcontainers wire logs — crop or redact).
