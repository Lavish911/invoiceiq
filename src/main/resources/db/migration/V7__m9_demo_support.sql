-- M9 demo support: seed-marker registry + per-tenant upload quota.
-- No existing domain tables are modified. Cleanup deletes tenant records
-- that are NOT registered here as seed; quota counters live in demo_quota.

CREATE TABLE demo_seed (
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID NOT NULL,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_demo_seed PRIMARY KEY (entity_type, entity_id)
);

CREATE INDEX idx_demo_seed_tenant ON demo_seed(tenant_id);

CREATE TABLE demo_quota (
    tenant_id UUID PRIMARY KEY REFERENCES tenant(id),
    window_start TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    upload_count INTEGER NOT NULL DEFAULT 0,
    bytes_total BIGINT NOT NULL DEFAULT 0
);
