package com.invoiceiq.invoice.controller;

import com.invoiceiq.common.exception.ResourceNotFoundException;
import com.invoiceiq.document.service.UploadNewInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "+ Upload New Invoice" entry point: creates a DRAFT invoice from a bare
 * document upload. Disabled by default via {@code app.upload-new.enabled};
 * while disabled the route behaves as if it does not exist (404).
 */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class UploadNewInvoiceController {

    private final UploadNewInvoiceService uploadNewInvoiceService;

    @Value("${app.upload-new.enabled:false}")
    private boolean uploadNewEnabled;

    @PostMapping(value = "/upload-new", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN','SUBMITTER')")
    public ResponseEntity<Map<String, Object>> uploadNewInvoice(
            @RequestParam("file") MultipartFile file) {
        if (!uploadNewEnabled) {
            throw new ResourceNotFoundException("Upload-new flow is disabled");
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UUID tenantId = com.invoiceiq.tenant.context.TenantContext.getCurrentTenant();
        UploadNewInvoiceService.UploadNewResult result =
                uploadNewInvoiceService.uploadNew(file, auth.getName(), tenantId);

        Map<String, Object> response = new HashMap<>();
        response.put("invoiceId", result.invoiceId);
        response.put("documentId", result.documentId);
        response.put("status", result.status);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
