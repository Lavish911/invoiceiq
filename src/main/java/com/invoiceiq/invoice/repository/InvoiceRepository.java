package com.invoiceiq.invoice.repository;

import com.invoiceiq.invoice.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    java.util.List<Invoice> findAllByTenantId(UUID tenantId);
    java.util.Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);
    void deleteByIdAndTenantId(UUID id, UUID tenantId);
}
