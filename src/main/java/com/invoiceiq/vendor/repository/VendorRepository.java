package com.invoiceiq.vendor.repository;

import com.invoiceiq.vendor.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, UUID> {
    Optional<Vendor> findByIdAndTenantId(UUID id, UUID tenantId);
}
