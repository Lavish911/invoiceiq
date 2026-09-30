package com.invoiceiq.document.repository;

import com.invoiceiq.document.entity.ExtractionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExtractionResultRepository extends JpaRepository<ExtractionResult, UUID> {
    Optional<ExtractionResult> findByDocumentIdAndTenantId(UUID documentId, UUID tenantId);
}
