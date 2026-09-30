package com.invoiceiq.document.controller;

import com.invoiceiq.document.entity.Document;
import com.invoiceiq.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices/{invoiceId}/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUBMITTER')")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @PathVariable UUID invoiceId,
            @RequestParam("file") MultipartFile file) {

        UUID tenantId = com.invoiceiq.tenant.context.TenantContext.getCurrentTenant();
        Document document = documentService.uploadDocument(file, invoiceId, tenantId);

        Map<String, Object> response = new HashMap<>();
        response.put("id", document.getId());
        response.put("status", document.getStatus());
        response.put("message", "Document uploaded and queued for extraction");

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUBMITTER', 'APPROVER')")
    public ResponseEntity<Document> getDocument(@PathVariable UUID invoiceId, @PathVariable UUID documentId) {
        UUID tenantId = com.invoiceiq.tenant.context.TenantContext.getCurrentTenant();
        Document document = documentService.getDocument(documentId, invoiceId, tenantId);
        return ResponseEntity.ok(document);
    }

    @GetMapping("/{documentId}/extraction")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUBMITTER', 'APPROVER')")
    public ResponseEntity<com.invoiceiq.document.entity.ExtractionResult> getExtractionResult(@PathVariable UUID invoiceId, @PathVariable UUID documentId) {
        UUID tenantId = com.invoiceiq.tenant.context.TenantContext.getCurrentTenant();
        com.invoiceiq.document.entity.ExtractionResult result = documentService.getExtractionResult(documentId, invoiceId, tenantId);
        return ResponseEntity.ok(result);
    }
}
