package com.invoiceiq.workflow.entity;

import com.invoiceiq.common.entity.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "workflow_rule")
@Getter
@Setter
public class WorkflowRule extends BaseTenantEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "minimum_amount")
    private BigDecimal minimumAmount;

    @Column(name = "maximum_amount")
    private BigDecimal maximumAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "required_approvals", nullable = false)
    private int requiredApprovals = 1;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
