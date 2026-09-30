DROP TABLE IF EXISTS approval_step CASCADE;
DROP TABLE IF EXISTS workflow_instance CASCADE;
DROP TABLE IF EXISTS workflow_rule CASCADE;
DROP TABLE IF EXISTS audit_log CASCADE;
DROP TABLE IF EXISTS notification CASCADE;

CREATE TABLE workflow_rule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL,
    minimum_amount NUMERIC(19, 4),
    maximum_amount NUMERIC(19, 4),
    currency VARCHAR(3),
    required_approvals INT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_workflow_rule_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id)
);

CREATE INDEX idx_workflow_rule_tenant_id ON workflow_rule(tenant_id);

CREATE TABLE workflow_instance (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    current_step INT NOT NULL DEFAULT 1,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_workflow_instance_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_workflow_instance_invoice FOREIGN KEY (invoice_id) REFERENCES invoice(id) ON DELETE CASCADE,
    CONSTRAINT uk_workflow_instance_invoice UNIQUE (invoice_id)
);

CREATE INDEX idx_workflow_instance_tenant_id ON workflow_instance(tenant_id);

CREATE TABLE approval_step (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    workflow_instance_id UUID NOT NULL,
    step_number INT NOT NULL,
    approver_role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    acted_by UUID,
    acted_at TIMESTAMP WITH TIME ZONE,
    comment TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_approval_step_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_approval_step_workflow FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instance(id) ON DELETE CASCADE,
    CONSTRAINT fk_approval_step_user FOREIGN KEY (acted_by) REFERENCES users(id),
    CONSTRAINT uk_approval_step_workflow_step UNIQUE (workflow_instance_id, step_number)
);

CREATE INDEX idx_approval_step_tenant_id ON approval_step(tenant_id);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    actor_id UUID,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    old_state TEXT,
    new_state TEXT,
    comment TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_audit_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_audit_log_user FOREIGN KEY (actor_id) REFERENCES users(id)
);

CREATE INDEX idx_audit_log_tenant_id ON audit_log(tenant_id);
CREATE INDEX idx_audit_log_entity ON audit_log(entity_type, entity_id);

CREATE TABLE notification (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    recipient_id UUID,
    recipient_role VARCHAR(50),
    channel VARCHAR(50) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_notification_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_notification_invoice FOREIGN KEY (invoice_id) REFERENCES invoice(id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_user FOREIGN KEY (recipient_id) REFERENCES users(id)
);

CREATE INDEX idx_notification_tenant_id ON notification(tenant_id);
