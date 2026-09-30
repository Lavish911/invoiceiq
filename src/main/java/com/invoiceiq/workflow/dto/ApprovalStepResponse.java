package com.invoiceiq.workflow.dto;

import com.invoiceiq.workflow.entity.ApprovalStepStatus;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ApprovalStepResponse {
    private UUID id;
    private int stepNumber;
    private String approverRole;
    private ApprovalStepStatus status;
    private UUID actedBy;
    private OffsetDateTime actedAt;
    private String comment;
}
