package com.invoiceiq.demo.repository;

import com.invoiceiq.demo.entity.DemoSeed;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DemoSeedRepository extends JpaRepository<DemoSeed, DemoSeed.DemoSeedId> {

    @Query("select s.entityId from DemoSeed s where s.tenantId = :tenantId and s.entityType = :entityType")
    List<UUID> findEntityIdsByTenantIdAndEntityType(
            @Param("tenantId") UUID tenantId,
            @Param("entityType") String entityType);
}
