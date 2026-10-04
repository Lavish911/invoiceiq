package com.invoiceiq.document.service;

import com.invoiceiq.auth.repository.UserRepository;
import com.invoiceiq.common.exception.ResourceNotFoundException;
import com.invoiceiq.demo.service.DemoQuotaService;
import com.invoiceiq.document.entity.Document;
import com.invoiceiq.document.entity.DocumentStatus;
import com.invoiceiq.document.repository.DocumentRepository;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import com.invoiceiq.vendor.entity.Vendor;
import com.invoiceiq.vendor.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.jobrunr.scheduling.JobScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/**
 * "+ Upload New Invoice" orchestration: creates a DRAFT invoice from a bare
 * document upload so callers never need a Vendor UUID. Reuses the existing
 * validation, storage, idempotency, and after-commit JobRunr mechanics.
 *
 * <p>Idempotency is derived from the file itself ({@code sha256:<hash>} for
 * both the key and the payload hash), so retrying an identical upload replays
 * the original invoice instead of creating another one.
 */
@Service
@RequiredArgsConstructor
public class UploadNewInvoiceService {

    private final VendorService vendorService;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentRepository documentRepository;
    private final JobScheduler jobScheduler;
    private final ExtractionJobService extractionJobService;
    private final DemoQuotaService demoQuotaService;

    public static class UploadNewResult {
        public final UUID invoiceId;
        public final UUID documentId;
        public final DocumentStatus status;
        public final boolean replayed;

        public UploadNewResult(UUID invoiceId, UUID documentId, DocumentStatus status, boolean replayed) {
            this.invoiceId = invoiceId;
            this.documentId = documentId;
            this.status = status;
            this.replayed = replayed;
            if (this.invoiceId == null || this.documentId == null || this.status == null) {
                throw new IllegalStateException("Upload-new result is incomplete");
            }
        }
    }

    @Transactional
    public UploadNewResult uploadNew(MultipartFile file, String submitterEmail, UUID tenantId) {
        String sanitizedFilename = DocumentService.validateAndSanitizeFilename(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to upload document", e);
        }
        String fileHash = sha256Hex(bytes);
        String idempotencyKey = "sha256:" + fileHash;

        var existing = invoiceRepository.findByIdempotencyKeyAndTenantId(idempotencyKey, tenantId);
        if (existing.isPresent()) {
            if (!fileHash.equals(existing.get().getRequestHash())) {
                throw new org.springframework.dao.DataIntegrityViolationException(
                        "Idempotency key already used with different payload");
            }
            Document existingDoc = documentRepository
                    .findFirstByInvoiceIdAndTenantIdOrderByCreatedAtDesc(
                            existing.get().getId(), tenantId)
                    .orElseThrow(() -> new IllegalStateException("Upload-new replay has no document"));
            return new UploadNewResult(
                    existing.get().getId(), existingDoc.getId(), existingDoc.getStatus(), true);
        }

        Vendor vendor = vendorService.getOrCreatePlaceholder(tenantId);
        var submitter = userRepository.findByEmailAndTenantId(submitterEmail, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setVendor(vendor);
        invoice.setSubmitterId(submitter.getId());
        invoice.setInvoiceNumber("INV-UPLOAD-" + UUID.randomUUID().toString().replace("-", ""));
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setCurrency("USD");
        invoice.setTotalAmount(BigDecimal.ZERO);
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setIdempotencyKey(idempotencyKey);
        invoice.setRequestHash(fileHash);
        invoice = invoiceRepository.save(invoice);

        demoQuotaService.checkAndRecord(tenantId, file.getSize());

        String storageKey = documentStorageService.storeFile(file, tenantId.toString());

        Document document = new Document();
        document.setTenantId(tenantId);
        document.setInvoice(invoice);
        document.setOriginalFilename(sanitizedFilename);
        document.setContentType(file.getContentType());
        document.setFileSize(file.getSize());
        document.setStorageKey(storageKey);
        document.setChecksum(fileHash);
        document.setStatus(DocumentStatus.UPLOADED);
        document = documentRepository.save(document);

        invoice.setS3Key(storageKey);
        invoiceRepository.save(invoice);

        final UUID docId = document.getId();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        jobScheduler.enqueue(() -> extractionJobService.processExtractionJob(docId, tenantId));
                    }
                });

        return new UploadNewResult(invoice.getId(), document.getId(), document.getStatus(), false);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to upload document", e);
        }
    }
}
