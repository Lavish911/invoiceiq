package com.invoiceiq.document.service;

import com.invoiceiq.document.dto.ExtractionResponse;
import com.invoiceiq.document.entity.Document;
import com.invoiceiq.document.entity.DocumentStatus;
import com.invoiceiq.document.entity.ExtractionResult;
import com.invoiceiq.document.entity.ExtractionStatus;
import com.invoiceiq.document.repository.DocumentRepository;
import com.invoiceiq.document.repository.ExtractionResultRepository;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.jobrunr.jobs.annotations.Job;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExtractionJobService {

    private final DocumentRepository documentRepository;
    private final ExtractionResultRepository extractionResultRepository;
    private final InvoiceRepository invoiceRepository;
    private final DocumentStorageService documentStorageService;
    private final PythonExtractionClient pythonExtractionClient;
    @org.springframework.beans.factory.annotation.Autowired @org.springframework.context.annotation.Lazy
    private ExtractionJobService self;

    @Job(name = "Extract Invoice Data")
    public void processExtractionJob(UUID documentId, UUID tenantId) {
        // Set context for background thread
        com.invoiceiq.tenant.context.TenantContext.setCurrentTenant(tenantId);
        
        try {
            boolean claimed = self.claimProcessing(documentId, tenantId);
            if (!claimed) {
                return;
            }

            ExtractionResponse response = null;
            Exception error = null;

            try {
                // Fetch Document to get storage key
                Document document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                        .orElseThrow(() -> new RuntimeException("Document not found"));

                // Get file
                byte[] fileBytes = documentStorageService.getFile(document.getStorageKey());

                // Call Python OCR outside of any DB transaction
                response = pythonExtractionClient.extractInvoiceData(
                        fileBytes, 
                        document.getOriginalFilename(),
                        tenantId.toString()
                );
            } catch (Exception e) {
                error = e;
            }

            self.saveExtractionResult(documentId, tenantId, response, error);

        } finally {
            com.invoiceiq.tenant.context.TenantContext.clear();
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public boolean claimProcessing(UUID documentId, UUID tenantId) {
        int rows = documentRepository.claimDocument(documentId, tenantId, DocumentStatus.PROCESSING, java.time.OffsetDateTime.now().minusMinutes(5));
        if (rows == 0) {
            return false;
        }

        Document document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Document not found after claiming"));

        Invoice invoice = document.getInvoice();
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoiceRepository.save(invoice);

        ExtractionResult result = extractionResultRepository.findByDocumentIdAndTenantId(documentId, tenantId)
                .orElseGet(() -> {
                    ExtractionResult newResult = new ExtractionResult();
                    newResult.setDocument(document);
                    newResult.setTenantId(tenantId);
                    return newResult;
                });
        result.setStatus(ExtractionStatus.IN_PROGRESS);
        extractionResultRepository.save(result);
        
        return true;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void saveExtractionResult(UUID documentId, UUID tenantId, ExtractionResponse response, Exception error) {
        Document document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        Invoice invoice = document.getInvoice();
        ExtractionResult result = extractionResultRepository.findByDocumentIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new RuntimeException("Extraction result not found"));

        if (error != null) {
            boolean isTransient = error instanceof org.springframework.web.client.RestClientException;
            if (error instanceof org.springframework.web.client.HttpStatusCodeException) {
                isTransient = ((org.springframework.web.client.HttpStatusCodeException) error).getStatusCode().is5xxServerError();
            }

            if (isTransient) {
                document.setStatus(DocumentStatus.UPLOADED);
                documentRepository.save(document);
                result.setStatus(ExtractionStatus.FAILED);
                result.setErrorMessage("Transient error: " + error.getMessage());
                extractionResultRepository.save(result);
                throw new RuntimeException("Transient extraction failure", error); // Let JobRunr retry
            } else {
                result.setStatus(ExtractionStatus.FAILED);
                result.setErrorMessage(error.getMessage());
                document.setStatus(DocumentStatus.EXTRACTION_FAILED);
                invoice.setStatus(InvoiceStatus.REJECTED);
            }
        } else if (response != null && "success".equals(response.getStatus())) {
            result.setExtractedData(response.getExtractedData());
            result.setConfidenceScores(response.getConfidenceScores());
            result.setStatus(ExtractionStatus.COMPLETED);
            
            document.setStatus(DocumentStatus.EXTRACTION_COMPLETED);

            // Populate invoice with basic extracted data
            if (response.getExtractedData() != null) {
                if (response.getExtractedData().containsKey("invoice_number")) {
                    String extractedNumber = (String) response.getExtractedData().get("invoice_number");
                    boolean collision = invoiceRepository.existsByTenantVendorAndNumber(tenantId, invoice.getVendor() != null ? invoice.getVendor().getId() : null, extractedNumber);
                    if (collision && !extractedNumber.equals(invoice.getInvoiceNumber())) {
                        result.setStatus(ExtractionStatus.FAILED);
                        result.setErrorMessage("Invoice number collision detected for extracted number: " + extractedNumber);
                        document.setStatus(DocumentStatus.EXTRACTION_FAILED);
                        invoice.setStatus(InvoiceStatus.REJECTED);
                        extractionResultRepository.save(result);
                        documentRepository.save(document);
                        invoiceRepository.save(invoice);
                        return; // exit safely, job is terminal
                    }
                    invoice.setInvoiceNumber(extractedNumber);
                }
                if (response.getExtractedData().containsKey("total_amount")) {
                    Object amount = response.getExtractedData().get("total_amount");
                    if (amount instanceof Number) {
                        invoice.setTotalAmount(BigDecimal.valueOf(((Number) amount).doubleValue()));
                    } else if (amount instanceof String) {
                        try {
                            invoice.setTotalAmount(new BigDecimal((String) amount));
                        } catch (Exception e) {
                            // ignore invalid format
                        }
                    }
                }
            }
            invoice.setStatus(InvoiceStatus.DRAFT);
        } else {
            result.setStatus(ExtractionStatus.FAILED);
            result.setErrorMessage(response != null ? response.getErrorMessage() : "Unknown error");
            document.setStatus(DocumentStatus.EXTRACTION_FAILED);
            invoice.setStatus(InvoiceStatus.REJECTED);
        }

        extractionResultRepository.save(result);
        documentRepository.save(document);
        invoiceRepository.save(invoice);
    }
}
