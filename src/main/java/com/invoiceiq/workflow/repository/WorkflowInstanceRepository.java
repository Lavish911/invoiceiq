package com.invoiceiq.workflow.repository;

import com.invoiceiq.workflow.entity.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {
    Optional<WorkflowInstance> findByInvoiceIdAndTenantId(UUID invoiceId, UUID tenantId);
    Optional<WorkflowInstance> findByIdAndTenantId(UUID id, UUID tenantId);
}
