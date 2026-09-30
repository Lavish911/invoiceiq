ALTER TABLE invoice ADD COLUMN idempotency_key VARCHAR(255);
CREATE INDEX idx_invoice_tenant_idempotency ON invoice(tenant_id, idempotency_key);
ALTER TABLE invoice ADD CONSTRAINT uk_invoice_tenant_vendor_number UNIQUE (tenant_id, vendor_id, invoice_number);
