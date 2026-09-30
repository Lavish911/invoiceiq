-- Make vendor_id NOT NULL as per M3 business model
ALTER TABLE invoice ALTER COLUMN vendor_id SET NOT NULL;

-- Add request_hash for idempotency payload comparison
ALTER TABLE invoice ADD COLUMN request_hash VARCHAR(256);

-- Replace the non-unique index with a partial unique index for idempotency
DROP INDEX idx_invoice_tenant_idempotency;
CREATE UNIQUE INDEX uk_invoice_tenant_idempotency ON invoice(tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
