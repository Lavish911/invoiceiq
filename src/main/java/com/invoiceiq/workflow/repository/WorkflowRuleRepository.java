package com.invoiceiq.workflow.repository;

import com.invoiceiq.workflow.entity.WorkflowRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowRuleRepository extends JpaRepository<WorkflowRule, UUID> {
    List<WorkflowRule> findByTenantIdOrderByPriorityAsc(UUID tenantId);
    Optional<WorkflowRule> findByIdAndTenantId(UUID id, UUID tenantId);
}
