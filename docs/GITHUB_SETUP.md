# GitHub Setup

## 1. Recommended repository name

`invoiceiq` — short, searchable, matches the artifact (`invoiceiq-backend`).

## 2. Recommended GitHub description

"AI-powered multi-tenant invoice automation platform built with Java Spring Boot, FastAPI, PostgreSQL, OCR, JWT/RBAC, and concurrency-safe approval workflows."

Every technology named is verified present: Java/Spring Boot backend, FastAPI OCR sidecar, PostgreSQL, Tesseract OCR, JWT + refresh rotation, ADMIN/APPROVER/SUBMITTER RBAC, multi-tenant isolation, latch-tested approval races.

## 3. Recommended topics

`java`, `spring-boot`, `postgresql`, `fastapi`, `ocr`, `tesseract`, `jwt`, `rbac`, `multi-tenancy`, `invoice-automation`, `backend`, `software-engineering`

(Deliberately no `redis`, `kafka`, `microservices`, or `machine-learning` — Redis is unwired, there is no broker or ML.)

## 4. What should be committed

- `src/`, `python-ocr/main.py`, `python-ocr/requirements.txt`, `python-ocr/Dockerfile`, `python-ocr/test_main.py`
- `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/`
- `docker-compose.yml`, `README.md`, `docs/`, `.gitignore`
- Small, static test fixtures only

## 5. What must NOT be committed

- `target/`, `*.class`, built jars
- `__pycache__/`, `.pytest_cache/`, `venv/`/`.venv/`
- `data/` (runtime uploads, including traversal-test artifacts), `*.log`, `stdout.log`, `output_template.txt`
- `.env` files or any real secret (see below)
- `.vscode/`, `.idea/`
- Docker volume contents (they live outside git by design)

All of the above are covered by the root `.gitignore`. Verify with `git status --porcelain` showing only intended source/docs before the first push.

## 6. Secrets check (do this before pushing)

- `src/main/resources/application.yml`: `secret-key: ${JWT_SECRET_KEY}` — placeholder only, safe.
- `AI_SERVICE_TOKEN` default `default_dev_token` exists in code and `python-ocr/main.py` — dev-only default; rotate in any deployed environment and never commit a real value.
- `src/test/resources/*.yml` contain a hardcoded test JWT secret — test-only, acceptable to commit, never reuse in production.
- Compose/test DB passwords (`invoiceiq_password`) are local-dev defaults — acceptable, documented as such.

## 7. Branch structure

- `main` — protected, V1.0 state, green CI only.
- `feature/*` — short-lived work branches merged via PR.
- `v1.0` tag on the release commit below.

## 8. Suggested first commit message

```text
docs: prepare InvoiceIQ for public GitHub release
```

## 9. License

No license file exists yet. Nothing here assumes one — pick explicitly (e.g. MIT for maximum portfolio reuse, or Apache-2.0) and add the `LICENSE` file before publishing. Do not publish a public repo with no license if you want others to legally use the code.
