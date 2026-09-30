package com.invoiceiq.workflow.repository;

import com.invoiceiq.workflow.entity.ApprovalStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, UUID> {
    Optional<ApprovalStep> findByWorkflowInstanceIdAndStepNumberAndTenantId(UUID workflowInstanceId, int stepNumber, UUID tenantId);
}
