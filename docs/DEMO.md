# InvoiceIQ Demonstration Flow

Prerequisite: a tenant and users exist (there is no self-registration endpoint — seed them directly, e.g. via the H2 console in tests or SQL inserts). The walkthrough below uses field names verified against the DTOs; every step was cross-checked with `docs/API.md`.

## 0. Seed a tenant + admin user (local dev only)

There is no sign-up endpoint, so insert one tenant and one user straight into PostgreSQL
(the password below is the BCrypt hash of `password` — local development only):

```sql
INSERT INTO tenant (id, name) VALUES
  ('11111111-1111-1111-1111-111111111111', 'Local Demo Co');

INSERT INTO users (id, tenant_id, email, password_hash, role) VALUES
  ('22222222-2222-2222-2222-222222222222',
   '11111111-1111-1111-1111-111111111111',
   'admin@local.test',
   '$2b$12$b0YbbfS5meSja12hms3bauqNKL2AkjW/HxqsuxuGC6GaozZ7xZ9/y',
   'ADMIN');
```

Log in below with `admin@local.test` / `password` / tenant `11111111-1111-1111-1111-111111111111`.

## 1. Login

```bash
POST /api/auth/login
{ "email": "submitter@company.com", "password": "password", "tenantId": "<tenant-uuid>" }
```

**Result**: `200 OK` with `{accessToken, refreshToken}`. Send `Authorization: Bearer <accessToken>` on everything below. (`tenantId` is required because one email may exist in several tenants.)

## 2. Create invoice (DRAFT)

```bash
POST /api/invoices
{
  "vendorId": "<vendor-uuid>",
  "invoiceNumber": "INV-2026-001",
  "invoiceDate": "2026-09-01",
  "dueDate": "2026-10-01",
  "currency": "USD",
  "lineItems": [{ "description": "Consulting", "quantity": 10, "unitPrice": 150.00 }]
}
# Optional replay safety:  X-Idempotency-Key: req-999
```

**Result**: `201 Created`. Totals are computed server-side from line items — there is no `totalAmount` request field. Repeating the call with the same idempotency key returns `200` with the same invoice; changing the payload under the same key returns `409`.

## 3. Upload document

```bash
POST /api/invoices/{invoiceId}/documents   (multipart/form-data, field: file=@invoice_scan.pdf)
```

**Result**: `202 Accepted` with `{id, status: UPLOADED}`. PDF/PNG/JPG only, ≤ 10 MB. A JobRunr job is enqueued after commit.

## 4. Poll extraction

```bash
GET /api/invoices/{invoiceId}/documents/{documentId}/extraction
```

**Result**: `ExtractionResult` with status `IN_PROGRESS` → `COMPLETED` (structured `extracted_data` + per-field confidence) or `FAILED` with an error message.

## 5. Review and update (optional)

```bash
PUT /api/invoices/{invoiceId}
{ "vendorId": "<vendor-uuid>", "invoiceNumber": "INV-2026-001",
  "invoiceDate": "2026-09-01", "dueDate": "2026-10-01", "currency": "USD",
  "lineItems": [{ "description": "Consulting", "quantity": 10, "unitPrice": 150.00 }],
  "version": 0 }
```

`version` (from the last read) is required; a stale version yields `409`.

## 6. Submit

```bash
POST /api/invoices/{invoiceId}/submit
```

**Result**: `DRAFT → NEEDS_REVIEW` plus a `SUBMITTED` audit event. Only the owning submitter or an ADMIN may submit; anything else → `403`; wrong state → `400`.

## 7. Initiate workflow

```bash
POST /api/invoices/{invoiceId}/workflow/initiate
```

**Result**: rule match → `PENDING_APPROVAL` with generated steps; no match → auto-`APPROVED`. A submitter notification record is persisted.

## 8. Approve (or reject) as APPROVER

```bash
POST /api/invoices/{invoiceId}/approval/approve
{ "comment": "Looks good, amounts match.", "expectedVersion": 0 }
```

**Result**: final approval → `APPROVED` (+ audit + submitter notification); `/approval/reject` → `REJECTED`. Repeating on a terminal workflow, or racing two approvers, yields `200` once and `409` for the loser.

## 9. Audit check

```bash
GET /api/invoices/{invoiceId}/audit
```

**Result**: newest-first events actually recorded by the system: `SUBMITTED` (DRAFT → NEEDS_REVIEW), `WORKFLOW_CREATED` (→ PENDING_APPROVAL), then `APPROVED` or `REJECTED` — each with actor, old/new state, comment, and timestamp. Cross-tenant reads return `404`.
