package com.invoiceiq.demo.repository;

import com.invoiceiq.demo.entity.DemoQuota;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DemoQuotaRepository extends JpaRepository<DemoQuota, UUID> {

    Optional<DemoQuota> findByTenantId(UUID tenantId);

    /**
     * Locked read: blocks until concurrent transactions commit, then returns
     * their committed version — the read-modify-write that follows is exact.
     * Only meaningful when the row already exists.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from DemoQuota q where q.tenantId = :tenantId")
    Optional<DemoQuota> findByTenantIdForUpdate(@Param("tenantId") UUID tenantId);
}
