package com.invoiceiq.invoice.repository; // Force IDE Sync

import com.invoiceiq.invoice.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"vendor", "lineItems"})
    Page<Invoice> findAllByTenantId(UUID tenantId, Pageable pageable);
    
    // Kept for backward compatibility if needed, though we should prefer Pageable
    java.util.List<Invoice> findAllByTenantId(UUID tenantId);
    
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"vendor", "lineItems"})
    java.util.Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);
    
    java.util.Optional<Invoice> findByIdempotencyKeyAndTenantId(String idempotencyKey, UUID tenantId);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(i) > 0 FROM Invoice i WHERE i.tenantId = :tenantId AND ((i.vendor IS NULL AND :vendorId IS NULL) OR i.vendor.id = :vendorId) AND i.invoiceNumber = :invoiceNumber")
    boolean existsByTenantVendorAndNumber(@org.springframework.data.repository.query.Param("tenantId") UUID tenantId, @org.springframework.data.repository.query.Param("vendorId") UUID vendorId, @org.springframework.data.repository.query.Param("invoiceNumber") String invoiceNumber);
    
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM Invoice i WHERE i.id = :id AND i.tenantId = :tenantId")
    void deleteByIdAndTenantId(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("tenantId") UUID tenantId);
}
