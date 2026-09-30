package com.invoiceiq.workflow.entity;

import com.invoiceiq.auth.entity.User;
import com.invoiceiq.common.entity.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "approval_step")
@Getter
@Setter
public class ApprovalStep extends BaseTenantEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @Column(name = "approver_role", nullable = false)
    private String approverRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ApprovalStepStatus status = ApprovalStepStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acted_by")
    private User actedBy;

    @Column(name = "acted_at")
    private OffsetDateTime actedAt;

    @Column(name = "comment")
    private String comment;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
