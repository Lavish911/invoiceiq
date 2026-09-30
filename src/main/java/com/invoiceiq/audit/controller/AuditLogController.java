package com.invoiceiq.audit.controller;

import com.invoiceiq.audit.dto.AuditLogResponse;
import com.invoiceiq.audit.entity.AuditLog;
import com.invoiceiq.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/invoices/{invoiceId}/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER', 'SUBMITTER')")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs(@PathVariable UUID invoiceId) {
        List<AuditLog> logs = auditLogService.getAuditLogsForEntity("INVOICE", invoiceId);
        
        List<AuditLogResponse> responses = logs.stream().map(log -> {
            AuditLogResponse response = new AuditLogResponse();
            response.setId(log.getId());
            response.setTenantId(log.getTenantId());
            if (log.getActor() != null) {
                response.setActorId(log.getActor().getId());
            }
            response.setEntityType(log.getEntityType());
            response.setEntityId(log.getEntityId());
            response.setAction(log.getAction());
            response.setOldState(log.getOldState());
            response.setNewState(log.getNewState());
            response.setComment(log.getComment());
            response.setCreatedAt(log.getCreatedAt());
            return response;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }
}
