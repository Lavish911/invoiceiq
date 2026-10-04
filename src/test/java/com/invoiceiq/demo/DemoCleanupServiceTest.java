package com.invoiceiq.demo;

import com.invoiceiq.demo.entity.DemoSeed;
import com.invoiceiq.demo.repository.DemoQuotaRepository;
import com.invoiceiq.demo.repository.DemoSeedRepository;
import com.invoiceiq.demo.service.DemoCleanupService;
import com.invoiceiq.document.entity.Document;
import com.invoiceiq.document.entity.ExtractionResult;
import com.invoiceiq.document.entity.ExtractionStatus;
import com.invoiceiq.document.repository.DocumentRepository;
import com.invoiceiq.document.repository.ExtractionResultRepository;
import com.invoiceiq.document.service.DocumentService;
import com.invoiceiq.document.service.DocumentStorageService;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import com.invoiceiq.tenant.context.TenantContext;
import com.invoiceiq.tenant.entity.Tenant;
import com.invoiceiq.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.demo.tenant-id=99999999-9999-9999-9999-999999999999",
        "app.demo.retention-hours=0"
})
class DemoCleanupServiceTest {

    private static final UUID DEMO_TENANT_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private DemoCleanupService demoCleanupService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ExtractionResultRepository extractionResultRepository;

    @Autowired
    private DemoSeedRepository demoSeedRepository;

    @Autowired
    private DemoQuotaRepository demoQuotaRepository;

    @MockBean
    private DocumentStorageService documentStorageService;

    private Tenant demoTenant;
    private Invoice seedInvoice;
    private Document seedDoc;

    @BeforeEach
    void setUp() {
        extractionResultRepository.deleteAll();
        documentRepository.deleteAll();
        invoiceRepository.deleteAll();
        tenantRepository.deleteAll();
        demoQuotaRepository.deleteAll();

        when(documentStorageService.storeFile(org.mockito.ArgumentMatchers.any(),
                anyString())).thenAnswer(invocation -> "demo/" + UUID.randomUUID());

        demoTenant = new Tenant();
        demoTenant.setId(DEMO_TENANT_ID);
        demoTenant.setName("Cleanup Demo Tenant");
        demoTenant = tenantRepository.save(demoTenant);

        seedInvoice = newInvoice(DEMO_TENANT_ID, "SEED-1");
        seedDoc = upload(seedInvoice);
        registerSeed("INVOICE", seedInvoice.getId());
        registerSeed("DOCUMENT", seedDoc.getId());
    }

    private Invoice newInvoice(UUID tenantId, String number) {
        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setInvoiceNumber(number + "-" + UUID.randomUUID());
        invoice.setTotalAmount(new BigDecimal("10.00"));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setSubmitterId(UUID.randomUUID());
        return invoiceRepository.save(invoice);
    }

    private Document upload(Invoice invoice) {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "dummy".getBytes());
        TenantContext.setCurrentTenant(invoice.getTenantId());
        try {
            return documentService.uploadDocument(file, invoice.getId(), invoice.getTenantId());
        } finally {
            TenantContext.clear();
        }
    }

    private void registerSeed(String entityType, UUID entityId) {
        DemoSeed seed = new DemoSeed();
        seed.setEntityType(entityType);
        seed.setEntityId(entityId);
        seed.setTenantId(DEMO_TENANT_ID);
        demoSeedRepository.save(seed);
    }

    @Test
    void expiredNonSeedDemoContentIsPurgedSeedAndForeignDataSurvive() {
        Invoice doomedInvoice = newInvoice(DEMO_TENANT_ID, "DOOMED");
        Document doomedDoc = upload(doomedInvoice);
        ExtractionResult extraction = new ExtractionResult();
        extraction.setTenantId(DEMO_TENANT_ID);
        extraction.setDocument(doomedDoc);
        extraction.setStatus(ExtractionStatus.FAILED);
        extractionResultRepository.save(extraction);

        Tenant other = new Tenant();
        other.setName("Foreign Tenant " + UUID.randomUUID());
        other = tenantRepository.save(other);
        Invoice foreignInvoice = newInvoice(other.getId(), "FOREIGN");
        Document foreignDoc = upload(foreignInvoice);

        demoCleanupService.performCleanup();

        assertTrue(documentRepository.findById(seedDoc.getId()).isPresent(),
                "seed document must survive");
        assertTrue(invoiceRepository.findById(seedInvoice.getId()).isPresent(),
                "seed invoice must survive");
        assertTrue(documentRepository.findById(foreignDoc.getId()).isPresent(),
                "foreign tenant document must survive");
        assertTrue(invoiceRepository.findById(foreignInvoice.getId()).isPresent(),
                "foreign tenant invoice must survive");

        assertTrue(documentRepository.findById(doomedDoc.getId()).isEmpty(),
                "expired non-seed document must be purged");
        assertTrue(invoiceRepository.findById(doomedInvoice.getId()).isEmpty(),
                "expired non-seed invoice must be purged");
        assertEquals(0, extractionResultRepository.count(),
                "extraction result of purged document must be gone");
        verify(documentStorageService, times(1)).deleteFile(doomedDoc.getStorageKey());
    }
}
