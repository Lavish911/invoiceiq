CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT uk_users_tenant_email UNIQUE (tenant_id, email)
);

CREATE INDEX idx_users_tenant_id ON users(tenant_id);

CREATE TABLE vendor (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    tax_id VARCHAR(100),
    address TEXT,
    bank_details TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_vendor_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

CREATE INDEX idx_vendor_tenant_id ON vendor(tenant_id);

CREATE TABLE invoice (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    vendor_id UUID,
    submitter_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_amount NUMERIC(19, 4),
    tax_amount NUMERIC(19, 4),
    currency VARCHAR(3),
    invoice_date DATE,
    due_date DATE,
    invoice_number VARCHAR(255),
    s3_key VARCHAR(1024),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_invoice_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_invoice_vendor FOREIGN KEY (vendor_id) REFERENCES vendor(id),
    CONSTRAINT fk_invoice_submitter FOREIGN KEY (submitter_id) REFERENCES users(id)
);

CREATE INDEX idx_invoice_tenant_id ON invoice(tenant_id);

CREATE TABLE invoice_line_item (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    description TEXT,
    quantity NUMERIC(19, 4),
    unit_price NUMERIC(19, 4),
    total_price NUMERIC(19, 4),
    CONSTRAINT fk_line_item_invoice FOREIGN KEY (invoice_id) REFERENCES invoice(id) ON DELETE CASCADE
);

CREATE INDEX idx_line_item_invoice_id ON invoice_line_item(invoice_id);

CREATE TABLE workflow_rule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    min_amount NUMERIC(19, 4),
    max_amount NUMERIC(19, 4),
    required_role VARCHAR(50) NOT NULL,
    step_order INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_workflow_rule_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

CREATE INDEX idx_workflow_rule_tenant_id ON workflow_rule(tenant_id);

CREATE TABLE approval_step (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL,
    workflow_rule_id UUID NOT NULL,
    approver_id UUID,
    status VARCHAR(50) NOT NULL,
    comments TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_approval_step_invoice FOREIGN KEY (invoice_id) REFERENCES invoice(id) ON DELETE CASCADE,
    CONSTRAINT fk_approval_step_rule FOREIGN KEY (workflow_rule_id) REFERENCES workflow_rule(id),
    CONSTRAINT fk_approval_step_approver FOREIGN KEY (approver_id) REFERENCES users(id)
);

CREATE INDEX idx_approval_step_invoice_id ON approval_step(invoice_id);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    user_id UUID,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    details JSON,
    CONSTRAINT fk_audit_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

CREATE INDEX idx_audit_log_tenant_id ON audit_log(tenant_id);
