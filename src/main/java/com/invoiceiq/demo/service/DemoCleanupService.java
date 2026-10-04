package com.invoiceiq.demo.service;

import com.invoiceiq.demo.repository.DemoSeedRepository;
import com.invoiceiq.document.service.DocumentStorageService;
import com.invoiceiq.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.jobrunr.scheduling.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hourly purge of expired non-seed demo-tenant content. Seed rows (registered
 * in {@code demo_seed}) and every other tenant are never touched. Inert unless
 * {@code app.demo.tenant-id} is configured; nothing is scheduled in that case,
 * so test profiles without the property stay behavior-free.
 */
@Service
@RequiredArgsConstructor
public class DemoCleanupService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoCleanupService.class);

    private final DemoSeedRepository demoSeedRepository;
    private final DocumentStorageService documentStorageService;
    private final JobScheduler jobScheduler;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.demo.tenant-id:}")
    private String demoTenantId;

    @Value("${app.demo.retention-hours:24}")
    private long retentionHours;

    @Override
    public void run(ApplicationArguments args) {
        if (isDemoEnabled()) {
            jobScheduler.scheduleRecurrently("0 * * * *", () -> performCleanup());
            log.info("Demo cleanup scheduled hourly for tenant {}", demoTenantId);
        }
    }

    @Transactional
    public void performCleanup() {
        if (!isDemoEnabled()) {
            return;
        }
        UUID tenantId = UUID.fromString(demoTenantId);
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(retentionHours);

        // The scheduled run has no request context; bind the demo tenant for the
        // fail-closed tenant filter (same pattern as ExtractionJobService).
        TenantContext.setCurrentTenant(tenantId);
        try {
            List<UUID> seedDocIds =
                    demoSeedRepository.findEntityIdsByTenantIdAndEntityType(tenantId, "DOCUMENT");
            List<Object[]> staleDocs = findStaleDocuments(tenantId, seedDocIds, cutoff);
            int docsDeleted = 0;
            for (Object[] row : staleDocs) {
                try {
                    deleteDocument(tenantId, (UUID) row[0], (String) row[1]);
                    docsDeleted++;
                } catch (Exception e) {
                    log.warn("Demo cleanup skipped document {}: {}", row[0], e.getMessage());
                }
            }

            List<UUID> seedInvoiceIds =
                    demoSeedRepository.findEntityIdsByTenantIdAndEntityType(tenantId, "INVOICE");
            List<UUID> staleInvoices = findStaleInvoices(tenantId, seedInvoiceIds, cutoff);
            int invoicesDeleted = 0;
            for (UUID invoiceId : staleInvoices) {
                try {
                    deleteInvoice(tenantId, invoiceId);
                    invoicesDeleted++;
                } catch (Exception e) {
                    log.warn("Demo cleanup skipped invoice {}: {}", invoiceId, e.getMessage());
                }
            }
            log.info("Demo cleanup removed {} documents and {} invoices for tenant {}",
                    docsDeleted, invoicesDeleted, tenantId);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isDemoEnabled() {
        return demoTenantId != null && !demoTenantId.isBlank();
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> findStaleDocuments(UUID tenantId, List<UUID> seedIds, OffsetDateTime cutoff) {
        String jpql = "select d.id, d.storageKey from Document d "
                + "where d.tenantId = :tenantId and d.createdAt < :cutoff";
        if (!seedIds.isEmpty()) {
            jpql += " and d.id not in :seedIds";
        }
        var query = entityManager.createQuery(jpql, Object[].class)
                .setParameter("tenantId", tenantId)
                .setParameter("cutoff", cutoff);
        if (!seedIds.isEmpty()) {
            query.setParameter("seedIds", seedIds);
        }
        return query.getResultList();
    }

    private void deleteDocument(UUID tenantId, UUID documentId, String storageKey) {
        entityManager.createQuery(
                        "delete from ExtractionResult r where r.document.id = :documentId")
                .setParameter("documentId", documentId)
                .executeUpdate();
        try {
            documentStorageService.deleteFile(storageKey);
        } catch (Exception e) {
            log.warn("Demo cleanup could not delete stored file {}: {}", storageKey, e.getMessage());
        }
        entityManager.createQuery(
                        "delete from Document d where d.id = :id and d.tenantId = :tenantId")
                .setParameter("id", documentId)
                .setParameter("tenantId", tenantId)
                .executeUpdate();
        deleteSeedMarker("DOCUMENT", documentId);
    }

    @SuppressWarnings("unchecked")
    private List<UUID> findStaleInvoices(UUID tenantId, List<UUID> seedIds, OffsetDateTime cutoff) {
        String jpql = "select i.id from Invoice i "
                + "where i.tenantId = :tenantId and i.createdAt < :cutoff";
        if (!seedIds.isEmpty()) {
            jpql += " and i.id not in :seedIds";
        }
        var query = entityManager.createQuery(jpql, UUID.class)
                .setParameter("tenantId", tenantId)
                .setParameter("cutoff", cutoff);
        if (!seedIds.isEmpty()) {
            query.setParameter("seedIds", seedIds);
        }
        return query.getResultList();
    }

    private void deleteInvoice(UUID tenantId, UUID invoiceId) {
        List<Object[]> docs = entityManager.createQuery(
                        "select d.id, d.storageKey from Document d "
                                + "where d.invoice.id = :invoiceId and d.tenantId = :tenantId",
                        Object[].class)
                .setParameter("invoiceId", invoiceId)
                .setParameter("tenantId", tenantId)
                .getResultList();
        for (Object[] row : docs) {
            deleteDocument(tenantId, (UUID) row[0], (String) row[1]);
        }
        List<UUID> docIds = docs.stream().map(row -> (UUID) row[0]).toList();
        entityManager.createQuery(
                        "delete from InvoiceLineItem l where l.invoice.id = :invoiceId")
                .setParameter("invoiceId", invoiceId)
                .executeUpdate();
        entityManager.createQuery(
                        "delete from ApprovalStep s where s.workflowInstance.id in "
                                + "(select w.id from WorkflowInstance w where w.invoice.id = :invoiceId)")
                .setParameter("invoiceId", invoiceId)
                .executeUpdate();
        entityManager.createQuery(
                        "delete from WorkflowInstance w where w.invoice.id = :invoiceId")
                .setParameter("invoiceId", invoiceId)
                .executeUpdate();
        entityManager.createQuery(
                        "delete from Notification n where n.invoice.id = :invoiceId")
                .setParameter("invoiceId", invoiceId)
                .executeUpdate();
        if (!docIds.isEmpty()) {
            entityManager.createQuery(
                            "delete from AuditLog a where a.entityType = 'DOCUMENT' "
                                    + "and a.entityId in :docIds")
                    .setParameter("docIds", docIds)
                    .executeUpdate();
        }
        entityManager.createQuery(
                        "delete from AuditLog a where a.entityType = 'INVOICE' and a.entityId = :invoiceId")
                .setParameter("invoiceId", invoiceId)
                .executeUpdate();
        entityManager.createQuery(
                        "delete from Invoice i where i.id = :id and i.tenantId = :tenantId")
                .setParameter("id", invoiceId)
                .setParameter("tenantId", tenantId)
                .executeUpdate();
        deleteSeedMarker("INVOICE", invoiceId);
    }

    private void deleteSeedMarker(String entityType, UUID entityId) {
        entityManager.createQuery(
                        "delete from DemoSeed s where s.entityType = :type and s.entityId = :id")
                .setParameter("type", entityType)
                .setParameter("id", entityId)
                .executeUpdate();
    }
}
