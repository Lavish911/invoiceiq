package com.invoiceiq.document.repository;

import com.invoiceiq.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {
    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("UPDATE Document d SET d.status = :newStatus WHERE d.id = :id AND d.tenantId = :tenantId AND (d.status = 'UPLOADED' OR d.status = 'EXTRACTION_FAILED' OR (d.status = 'PROCESSING' AND d.updatedAt < :staleThreshold))")
    int claimDocument(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("tenantId") UUID tenantId, @org.springframework.data.repository.query.Param("newStatus") com.invoiceiq.document.entity.DocumentStatus newStatus, @org.springframework.data.repository.query.Param("staleThreshold") java.time.OffsetDateTime staleThreshold);
}
