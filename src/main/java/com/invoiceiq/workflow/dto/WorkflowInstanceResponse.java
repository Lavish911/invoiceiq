package com.invoiceiq.workflow.dto;

import com.invoiceiq.workflow.entity.WorkflowStatus;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class WorkflowInstanceResponse {
    private UUID id;
    private UUID tenantId;
    private UUID invoiceId;
    private WorkflowStatus status;
    private int currentStep;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<ApprovalStepResponse> approvalSteps;
}
