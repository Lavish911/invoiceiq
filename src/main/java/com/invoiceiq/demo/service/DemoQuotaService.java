package com.invoiceiq.demo.service;

import com.invoiceiq.common.exception.DemoQuotaExceededException;
import com.invoiceiq.demo.entity.DemoQuota;
import com.invoiceiq.demo.repository.DemoQuotaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling per-tenant upload quota for the demo tenant. Inert unless
 * {@code app.demo.tenant-id} is configured. Admission is a single atomic
 * UPDATE, so concurrent uploads serialize on the row lock and the cap holds
 * exactly — there is no read-modify-write race by construction.
 */
@Service
@RequiredArgsConstructor
public class DemoQuotaService {

    private final DemoQuotaRepository demoQuotaRepository;

    /**
     * Per-tenant JVM mutex. Makes the check-and-record exact within one
     * application instance (including H2-backed tests); the database row lock
     * underneath keeps separate instances correct on PostgreSQL.
     */
    private static final ConcurrentHashMap<UUID, Object> TENANT_LOCKS = new ConcurrentHashMap<>();

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.demo.tenant-id:}")
    private String demoTenantId;

    @Value("${app.demo.max-uploads-per-hour:20}")
    private int maxUploadsPerHour;

    @Value("${app.demo.max-bytes-total:209715200}")
    private long maxBytesTotal;

    /**
     * Runs in its own transaction so first-insert races surface eagerly inside
     * this method (catchable below) instead of at the outer upload commit.
     * Trade-off: an upload that fails after admission keeps its quota charge —
     * acceptable over-counting for abuse control, and uploads that fail
     * validation never reach this call.
     */
    /**
     * Runs in its own transaction so lock waits stay short and first-insert
     * races surface eagerly inside this method (catchable below) instead of
     * at the outer upload commit. Trade-off: an upload that fails after
     * admission keeps its quota charge — acceptable over-counting for abuse
     * control, and uploads that fail validation never reach this call.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void checkAndRecord(UUID tenantId, long fileSizeBytes) {
        if (demoTenantId == null || demoTenantId.isBlank() || !demoTenantId.equals(tenantId.toString())) {
            return;
        }
        synchronized (TENANT_LOCKS.computeIfAbsent(tenantId, key -> new Object())) {
            admit(tenantId, fileSizeBytes);
        }
    }

    private void admit(UUID tenantId, long fileSizeBytes) {
        if (demoQuotaRepository.findByTenantId(tenantId).isEmpty()) {
            DemoQuota created = new DemoQuota();
            created.setTenantId(tenantId);
            try {
                demoQuotaRepository.saveAndFlush(created);
            } catch (DataIntegrityViolationException race) {
                // Another thread created the row first. The failed insert must
                // be evicted or Hibernate replays it on the next flush.
                entityManager.clear();
            }
        }
        // Locked read: blocks until concurrent admitters commit, then returns
        // the latest committed version — the check below is exact.
        DemoQuota quota = demoQuotaRepository.findByTenantIdForUpdate(tenantId)
                .orElseThrow(() -> new IllegalStateException("Demo quota row missing"));
        if (quota.getWindowStart().isBefore(OffsetDateTime.now().minusHours(1))) {
            quota.setWindowStart(OffsetDateTime.now());
            quota.setUploadCount(0);
            quota.setBytesTotal(0L);
        }
        if (quota.getUploadCount() >= maxUploadsPerHour
                || quota.getBytesTotal() + fileSizeBytes > maxBytesTotal) {
            throw new DemoQuotaExceededException(
                    "Demo upload limit reached. Please try again later.");
        }
        quota.setUploadCount(quota.getUploadCount() + 1);
        quota.setBytesTotal(quota.getBytesTotal() + fileSizeBytes);
        demoQuotaRepository.save(quota);
    }
}
