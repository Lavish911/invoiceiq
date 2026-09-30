# API Reference

Verified against controllers, DTOs, and `GlobalExceptionHandler`. All secured endpoints require `Authorization: Bearer <accessToken>`. Errors use a uniform `ApiError` envelope (`timestamp`, `status`, `error`, `message`, `path`).

## Authentication (`/api/auth`)

### `POST /api/auth/login`
- **Auth**: none. **Body**: `email`, `password`, `tenantId` (all required — the same email may exist in two tenants).
- **Response**: `200 OK` with `{accessToken, refreshToken}`.
- **Errors**: `401` invalid credentials.

### `POST /api/auth/refresh`
- **Auth**: none (valid refresh-token string in body: `refreshToken`).
- **Response**: `200 OK` with a rotated `{accessToken, refreshToken}` pair.
- **Errors**: `401` unknown/expired/revoked/rotated token.

## Invoices (`/api/invoices`)

### `POST /api/invoices`
- **Roles**: `ADMIN`, `SUBMITTER`.
- **Body**: `vendorId`, `invoiceNumber`, `invoiceDate`, `dueDate`, `currency`, `lineItems[]` (`description`, `quantity`, `unitPrice`); totals are computed server-side and cannot be submitted. Optional `X-Idempotency-Key` header.
- **Response**: `201 Created` (new) or `200 OK` (idempotent replay of identical payload).
- **Errors**: `400` validation; `409` duplicate `(tenant, vendor, number)` or conflicting idempotency reuse.

### `GET /api/invoices?page=&size=&sort=`
- **Roles**: all. **Tenant-scoped**, paged (size capped at 100), sort restricted to a whitelist (`invoiceNumber`, `invoiceDate`, `dueDate`, `totalAmount`, `createdAt`, `updatedAt`); anything else is `400`.

### `GET /api/invoices/{id}`
- **Roles**: all. **Errors**: `404` when missing **or** belonging to another tenant (no existence leak).

### `PUT /api/invoices/{id}`
- **Roles**: `ADMIN`, `APPROVER`. **Body**: same as create plus required `version`.
- **Errors**: `400` validation; `404` cross-tenant/missing; `409` stale version or duplicate collision.

### `POST /api/invoices/{id}/submit`
- **Roles**: `ADMIN`, or the owning `SUBMITTER`. Transitions `DRAFT → NEEDS_REVIEW` (+ `SUBMITTED` audit event).
- **Response**: `200 OK` with the invoice. **Errors**: `403` non-owner; `400` wrong state; `404` cross-tenant/missing.

### `DELETE /api/invoices/{id}`
- **Roles**: `ADMIN` only → `204 No Content`; others `403`.

## Documents & Extraction (`/api/invoices/{invoiceId}/documents`)

### `POST …/documents`
- **Roles**: `ADMIN`, `SUBMITTER`. Multipart `file` (PDF/PNG/JPG, non-empty, ≤ 10 MB).
- **Response**: `202 Accepted` with `{id, status, message}`; extraction runs in background.
- **Errors**: `400` invalid file; `404` cross-tenant/missing invoice.

### `GET …/documents/{documentId}` and `GET …/documents/{documentId}/extraction`
- **Roles**: all. Tenant-scoped with invoice-membership check (`404` otherwise). Extraction returns the `ExtractionResult` entity (`IN_PROGRESS` / `COMPLETED` / `FAILED`, structured `extracted_data`, per-field confidence).

## Workflow (`/api/invoices/{invoiceId}/…`)

### `POST …/workflow/initiate` (ADMIN, SUBMITTER)
Matches active tenant rules by priority (amount window + currency); creates instance + sequential steps and moves to `PENDING_APPROVAL`, or auto-approves when nothing matches. Response `200` empty. Errors: `400` wrong state; `404` cross-tenant/missing.

### `GET …/workflow` (all roles)
Returns the instance with steps, or `404` if none / other tenant.

### `POST …/approval/approve`, `POST …/approval/reject` (ADMIN, APPROVER)
Body: `ApprovalRequest` (`comment` optional; `expectedVersion` required). Terminal `APPROVED`/`REJECTED` on final step with audit + submitter notification. Errors: `403` wrong role; `404` cross-tenant/missing; **`409`** terminal re-entry, completed step, or stale version.

## Workflow rules (`/api/workflow-rules`, ADMIN only)

`POST` → `201`; `GET`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}`. Body: `name`, `active`, `priority`, `minimumAmount`, `maximumAmount`, `currency`, `requiredApprovals`. All tenant-scoped (`404` cross-tenant).

## Audit (`GET /api/invoices/{invoiceId}/audit`, all roles)

Tenant-scoped, invoice-membership enforced, newest-first list of `{action, old/new state, actor, comment, timestamp}`. Recorded actions: `SUBMITTED`, `WORKFLOW_CREATED`, `AUTO_APPROVE`, `STEP_APPROVED`, `APPROVED`, `REJECTED`. No mutation endpoints exist.
