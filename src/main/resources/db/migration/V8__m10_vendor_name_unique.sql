-- M10: race-safe get-or-create for the per-tenant "Unassigned Vendor"
-- placeholder. Converts a would-be silent duplicate into a catchable
-- unique violation. Declared with the same name in the Vendor entity so
-- Flyway validation and JPA metadata agree. Pre-migration gate: no
-- duplicate (tenant_id, name) rows may exist (checked on the target
-- database before applying).
ALTER TABLE vendor ADD CONSTRAINT uk_vendor_tenant_name UNIQUE (tenant_id, name);
