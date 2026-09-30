package com.invoiceiq.workflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class WorkflowRuleRequest {
    @NotBlank
    private String name;
    
    private boolean active = true;
    
    @Min(1)
    private int priority;
    
    private BigDecimal minimumAmount;
    private BigDecimal maximumAmount;
    private String currency;
    
    @Min(1)
    private int requiredApprovals = 1;
}
