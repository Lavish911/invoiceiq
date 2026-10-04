package com.invoiceiq.demo;

import com.invoiceiq.common.exception.DemoQuotaExceededException;
import com.invoiceiq.demo.repository.DemoQuotaRepository;
import com.invoiceiq.document.repository.DocumentRepository;
import com.invoiceiq.document.repository.ExtractionResultRepository;
import com.invoiceiq.document.service.DocumentService;
import com.invoiceiq.tenant.context.TenantContext;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import com.invoiceiq.tenant.entity.Tenant;
import com.invoiceiq.tenant.repository.TenantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.demo.tenant-id=99999999-9999-9999-9999-999999999999",
        "app.demo.max-uploads-per-hour=2",
        "app.demo.max-bytes-total=10485760"
})
class DemoQuotaTest {

    private static final UUID DEMO_TENANT_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");

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
    private DemoQuotaRepository demoQuotaRepository;

    @MockBean
    private com.invoiceiq.document.service.DocumentStorageService documentStorageService;

    private Invoice invoice;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        extractionResultRepository.deleteAll();
        documentRepository.deleteAll();
        invoiceRepository.deleteAll();
        tenantRepository.deleteAll();
        demoQuotaRepository.deleteAll();

        Tenant tenant = new Tenant();
        tenant.setId(DEMO_TENANT_ID);
        tenant.setName("Quota Demo Tenant");
        tenantRepository.save(tenant);

        invoice = new Invoice();
        invoice.setTenantId(DEMO_TENANT_ID);
        invoice.setInvoiceNumber("QUOTA-" + UUID.randomUUID());
        invoice.setTotalAmount(new BigDecimal("10.00"));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setSubmitterId(UUID.randomUUID());
        invoice = invoiceRepository.save(invoice);

        when(documentStorageService.storeFile(any(), anyString()))
                .thenAnswer(invocation -> "quota/" + UUID.randomUUID());
    }

    private MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/pdf", "dummy".getBytes());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void uploadsWithinQuotaSucceedThenQuotaRejects() {
        TenantContext.setCurrentTenant(DEMO_TENANT_ID);
        documentService.uploadDocument(file("a.pdf"), invoice.getId(), DEMO_TENANT_ID);
        documentService.uploadDocument(file("b.pdf"), invoice.getId(), DEMO_TENANT_ID);
        assertThrows(DemoQuotaExceededException.class,
                () -> documentService.uploadDocument(file("c.pdf"), invoice.getId(), DEMO_TENANT_ID));
    }

    @Test
    void quotaDoesNotApplyToOtherTenants() {
        TenantContext.setCurrentTenant(DEMO_TENANT_ID);
        Tenant other = new Tenant();
        other.setName("Other Tenant " + UUID.randomUUID());
        other = tenantRepository.save(other);

        Invoice otherInvoice = new Invoice();
        otherInvoice.setTenantId(other.getId());
        otherInvoice.setInvoiceNumber("OTHER-" + UUID.randomUUID());
        otherInvoice.setTotalAmount(new BigDecimal("10.00"));
        otherInvoice.setStatus(InvoiceStatus.DRAFT);
        otherInvoice.setSubmitterId(UUID.randomUUID());
        otherInvoice = invoiceRepository.save(otherInvoice);

        TenantContext.setCurrentTenant(other.getId());
        try {
            for (int i = 0; i < 5; i++) {
                documentService.uploadDocument(file("o" + i + ".pdf"), otherInvoice.getId(), other.getId());
            }
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void concurrentUploadsStayWithinCap() throws Exception {
        int threads = 6;
        // One invoice per thread: uploads must not contend on the shared
        // invoice row (optimistic locking would rightly reject that); the
        // quota row is the only intended contention point.
        TenantContext.setCurrentTenant(DEMO_TENANT_ID);
        List<Invoice> invoices = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                Invoice inv = new Invoice();
                inv.setTenantId(DEMO_TENANT_ID);
                inv.setInvoiceNumber("RACE-" + UUID.randomUUID());
                inv.setTotalAmount(new BigDecimal("10.00"));
                inv.setStatus(InvoiceStatus.DRAFT);
                inv.setSubmitterId(UUID.randomUUID());
                invoices.add(invoiceRepository.save(inv));
            }
        } finally {
            TenantContext.clear();
        }
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            final UUID invoiceId = invoices.get(idx).getId();
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                TenantContext.setCurrentTenant(DEMO_TENANT_ID);
                try {
                    documentService.uploadDocument(
                            file("race" + idx + ".pdf"), invoiceId, DEMO_TENANT_ID);
                    succeeded.incrementAndGet();
                } catch (DemoQuotaExceededException e) {
                    rejected.incrementAndGet();
                } finally {
                    TenantContext.clear();
                }
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertEquals(2, succeeded.get(), "exactly the cap must succeed under race");
        assertEquals(4, rejected.get(), "the rest must be quota-rejected");
    }
}
