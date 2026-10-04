package com.invoiceiq.document.service;

import com.invoiceiq.document.entity.Document;
import com.invoiceiq.document.entity.DocumentStatus;
import com.invoiceiq.document.repository.DocumentRepository;
import com.invoiceiq.demo.service.DemoQuotaService;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final InvoiceRepository invoiceRepository;
    private final com.invoiceiq.document.repository.ExtractionResultRepository extractionResultRepository;
    private final DocumentStorageService documentStorageService;
    private final JobScheduler jobScheduler;
    private final ExtractionJobService extractionJobService;
    private final DemoQuotaService demoQuotaService;

    @Transactional
    public Document uploadDocument(MultipartFile file, UUID invoiceId, UUID tenantId) {
        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found or access denied"));

        if (invoice.getStatus() != InvoiceStatus.DRAFT && invoice.getStatus() != InvoiceStatus.REJECTED) {
            throw new IllegalStateException("Can only upload documents to DRAFT or REJECTED invoices");
        }

        String sanitizedFilename = validateAndSanitizeFilename(file);
        try {
            // Generate checksum
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            String checksum = hexString.toString();

            // Demo-tenant upload quota (no-op unless the demo tenant is configured)
            demoQuotaService.checkAndRecord(tenantId, file.getSize());

            // Store file
            String storageKey = documentStorageService.storeFile(file, tenantId.toString());

            // Create document
            Document document = new Document();
            document.setTenantId(tenantId);
            document.setInvoice(invoice);
            document.setOriginalFilename(sanitizedFilename);
            document.setContentType(file.getContentType());
            document.setFileSize(file.getSize());
            document.setStorageKey(storageKey);
            document.setChecksum(checksum);
            document.setStatus(DocumentStatus.UPLOADED);
            document = documentRepository.save(document);

            // Update invoice S3 key reference
            invoice.setS3Key(storageKey);
            invoiceRepository.save(invoice);

            // Enqueue JobRunr job AFTER commit
            final UUID docId = document.getId();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        jobScheduler.enqueue(() -> extractionJobService.processExtractionJob(docId, tenantId));
                    }
                }
            );

            return document;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to upload document", e);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to upload document", e);
        }
    }

    /**
     * Shared upload validation + basename sanitization. Extracted so the
     * upload-new flow enforces byte-identical rules without duplicating them.
     * Returns the sanitized basename.
     */
    static String validateAndSanitizeFilename(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > 10 * 1024 * 1024) { // 10MB
            throw new IllegalArgumentException("File size exceeds limit");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lowerName = originalFilename.toLowerCase();
            if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".png") && !lowerName.endsWith(".jpg") && !lowerName.endsWith(".jpeg")) {
                throw new IllegalArgumentException("Unsupported file type");
            }
        } else {
            throw new IllegalArgumentException("Filename is missing");
        }
        return java.nio.file.Paths.get(originalFilename).getFileName().toString();
    }

    @Transactional(readOnly = true)
    public Document getDocument(UUID documentId, UUID invoiceId, UUID tenantId) {
        Document document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (!document.getInvoice().getId().equals(invoiceId)) {
            throw new IllegalArgumentException("Document does not belong to the specified invoice");
        }
        return document;
    }

    @Transactional(readOnly = true)
    public com.invoiceiq.document.entity.ExtractionResult getExtractionResult(UUID documentId, UUID invoiceId, UUID tenantId) {
        Document document = getDocument(documentId, invoiceId, tenantId);
        return extractionResultRepository.findByDocumentIdAndTenantId(document.getId(), tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Extraction result not found"));
    }
}
