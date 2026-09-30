package com.invoiceiq.audit.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class AuditLogResponse {
    private UUID id;
    private UUID tenantId;
    private UUID actorId;
    private String entityType;
    private UUID entityId;
    private String action;
    private String oldState;
    private String newState;
    private String comment;
    private OffsetDateTime createdAt;
}
