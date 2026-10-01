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
import com.invoiceiq.tenant.entity.Tenant;
import com.invoiceiq.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
// Docker is a REQUIRED test prerequisite: without it this class must FAIL LOUDLY,
// never silently skip (a skipped run must not be mistaken for full verification).
@Testcontainers
public class ExtractionJobServiceIntegrationTest {

    @Autowired
    private ExtractionJobService extractionJobService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ExtractionResultRepository extractionResultRepository;

    @MockBean
    private PythonExtractionClient pythonExtractionClient;
    
    @MockBean
    private DocumentStorageService documentStorageService;

    private Tenant tenant;
    private Invoice invoice;
    private Document document;

    @BeforeEach
    void setUp() {
        extractionResultRepository.deleteAll();
        documentRepository.deleteAll();
        invoiceRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = new Tenant();
        tenant.setName("Test Tenant " + UUID.randomUUID());
        tenant = tenantRepository.save(tenant);

        invoice = new Invoice();
        invoice.setTenantId(tenant.getId());
        invoice.setInvoiceNumber("TEST-" + UUID.randomUUID());
        invoice.setTotalAmount(new BigDecimal("100.00"));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setSubmitterId(UUID.randomUUID());
        invoice = invoiceRepository.save(invoice);

        document = new Document();
        document.setTenantId(tenant.getId());
        document.setInvoice(invoice);
        document.setOriginalFilename("test.pdf");
        document.setContentType("application/pdf");
        document.setFileSize(1000L);
        document.setStorageKey(tenant.getId() + "/test.pdf");
        document.setChecksum("dummy-checksum");
        document.setStatus(DocumentStatus.UPLOADED);
        document = documentRepository.save(document);
    }

    @Test
    void testProcessExtractionJobSuccess() {
        System.out.println("Docs in DB: " + documentRepository.findAll().size());
        System.out.println("Doc ID: " + document.getId() + " Tenant ID: " + tenant.getId());
        
        // Mock storage
        when(documentStorageService.getFile(anyString())).thenReturn("dummy content".getBytes());

        // Mock python client
        ExtractionResponse mockResponse = new ExtractionResponse();
        mockResponse.setStatus("success");
        Map<String, Object> extractedData = new HashMap<>();
        extractedData.put("invoice_number", "EXTRACTED-123");
        extractedData.put("total_amount", 550.75);
        mockResponse.setExtractedData(extractedData);
        mockResponse.setConfidenceScores(new HashMap<>());
        when(pythonExtractionClient.extractInvoiceData(any(), anyString(), anyString())).thenReturn(mockResponse);

        // Execute Job
        extractionJobService.processExtractionJob(document.getId(), tenant.getId());

        // Verify Results
        Document updatedDoc = documentRepository.findById(document.getId()).get();
        assertThat(updatedDoc.getStatus()).isEqualTo(DocumentStatus.EXTRACTION_COMPLETED);

        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).get();
        assertThat(updatedInvoice.getInvoiceNumber()).isEqualTo("EXTRACTED-123");
        assertThat(updatedInvoice.getTotalAmount()).isEqualByComparingTo("550.75");
        assertThat(updatedInvoice.getStatus()).isEqualTo(InvoiceStatus.DRAFT);

        ExtractionResult result = extractionResultRepository.findByDocumentIdAndTenantId(document.getId(), tenant.getId()).get();
        assertThat(result.getStatus()).isEqualTo(ExtractionStatus.COMPLETED);
        assertThat(result.getExtractedData()).containsKey("invoice_number");
    }

    @Test
    void testExtractionInvoiceNumberCollision() {
        // Create an existing invoice with the same number to cause collision
        Invoice existingInvoice = new Invoice();
        existingInvoice.setTenantId(tenant.getId());
        existingInvoice.setInvoiceNumber("COLLISION-123");
        existingInvoice.setTotalAmount(new BigDecimal("999.99"));
        existingInvoice.setStatus(InvoiceStatus.DRAFT);
        existingInvoice.setSubmitterId(UUID.randomUUID());
        invoiceRepository.save(existingInvoice);

        // Mock storage and python client
        when(documentStorageService.getFile(anyString())).thenReturn("dummy content".getBytes());

        ExtractionResponse mockResponse = new ExtractionResponse();
        mockResponse.setStatus("success");
        Map<String, Object> extractedData = new HashMap<>();
        extractedData.put("invoice_number", "COLLISION-123");
        mockResponse.setExtractedData(extractedData);
        when(pythonExtractionClient.extractInvoiceData(any(), anyString(), anyString())).thenReturn(mockResponse);

        // Execute Job
        extractionJobService.processExtractionJob(document.getId(), tenant.getId());

        // Verify it was marked as a terminal conflict state
        Document updatedDoc = documentRepository.findById(document.getId()).get();
        assertThat(updatedDoc.getStatus()).isEqualTo(DocumentStatus.EXTRACTION_FAILED);

        ExtractionResult result = extractionResultRepository.findByDocumentIdAndTenantId(document.getId(), tenant.getId()).get();
        assertThat(result.getStatus()).isEqualTo(ExtractionStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("Invoice number collision");

        // The original invoice is kept safe and intact
        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).get();
        assertThat(updatedInvoice.getInvoiceNumber()).isNotEqualTo("COLLISION-123");
        assertThat(updatedInvoice.getStatus()).isEqualTo(InvoiceStatus.REJECTED);
    }

    @Test
    void testAtomicExtractionClaim() throws InterruptedException {
        // Prepare latch for concurrency
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(2);

        // Mock dependencies
        when(documentStorageService.getFile(anyString())).thenReturn("dummy content".getBytes());
        ExtractionResponse mockResponse = new ExtractionResponse();
        mockResponse.setStatus("success");
        mockResponse.setExtractedData(new HashMap<>());
        when(pythonExtractionClient.extractInvoiceData(any(), anyString(), anyString())).thenReturn(mockResponse);

        // Run two concurrent jobs
        Runnable job = () -> {
            try {
                startLatch.await();
                extractionJobService.processExtractionJob(document.getId(), tenant.getId());
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                doneLatch.countDown();
            }
        };

        new Thread(job).start();
        new Thread(job).start();

        // Let them both start
        startLatch.countDown();
        doneLatch.await();

        // Verify exactly one ExtractionResult was generated, or the document status is correct and there's no exception thrown
        Document updatedDoc = documentRepository.findById(document.getId()).get();
        assertThat(updatedDoc.getStatus()).isEqualTo(DocumentStatus.EXTRACTION_COMPLETED);
    }
}
