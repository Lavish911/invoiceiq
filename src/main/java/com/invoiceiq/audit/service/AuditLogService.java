package com.invoiceiq.audit.service;

import com.invoiceiq.audit.entity.AuditLog;
import com.invoiceiq.audit.repository.AuditLogRepository;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.UserRepository;
import com.invoiceiq.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recordEvent(String entityType, UUID entityId, String action, String oldState, String newState, String comment, UUID actorId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context found");
        }

        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setAction(action);
        log.setOldState(oldState);
        log.setNewState(newState);
        log.setComment(comment);

        if (actorId != null) {
            userRepository.findById(actorId).ifPresent(log::setActor);
        }

        auditLogRepository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLogsForEntity(String entityType, UUID entityId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context found");
        }
        return auditLogRepository.findByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDesc(entityType, entityId, tenantId);
    }
}
