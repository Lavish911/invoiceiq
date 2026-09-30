package com.invoiceiq.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApprovalRequest {
    private String comment;
    @NotNull
    private Long expectedVersion; // For idempotency/concurrency check on the workflow instance or invoice
}
