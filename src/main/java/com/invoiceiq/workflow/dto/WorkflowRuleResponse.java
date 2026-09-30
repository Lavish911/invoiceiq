package com.invoiceiq.workflow.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class WorkflowRuleResponse {
    private UUID id;
    private UUID tenantId;
    private String name;
    private boolean active;
    private int priority;
    private BigDecimal minimumAmount;
    private BigDecimal maximumAmount;
    private String currency;
    private int requiredApprovals;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private Long version;
}
