package com.invoiceiq.document.service;

import com.invoiceiq.tenant.context.TenantContext;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public class RealOcrIntegrationTest {

    @Container
    public static GenericContainer<?> pythonOcrContainer = new GenericContainer<>(
            new ImageFromDockerfile()
                    .withFileFromPath("Dockerfile", Path.of("python-ocr/Dockerfile"))
                    .withFileFromPath("main.py", Path.of("python-ocr/main.py"))
                    .withFileFromPath("requirements.txt", Path.of("python-ocr/requirements.txt"))
                    .withFileFromPath("test_main.py", Path.of("python-ocr/test_main.py"))
    )
            .withExposedPorts(8000)
            // Wait for FastAPI to be up
            .waitingFor(Wait.forLogMessage(".*Application startup complete.*\\n", 1));

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ocr.service.url", () -> "http://" + pythonOcrContainer.getHost() + ":" + pythonOcrContainer.getFirstMappedPort());
        registry.add("AI_SERVICE_TOKEN", () -> "default_dev_token");
    }

    @Autowired
    private DocumentService documentService;
    
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

    @Autowired
    private DocumentStorageService documentStorageService;

    private Tenant tenant;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        extractionResultRepository.deleteAll();
        documentRepository.deleteAll();
        invoiceRepository.deleteAll();
        tenantRepository.deleteAll();

        tenant = new Tenant();
        tenant.setName("OCR Tenant " + UUID.randomUUID());
        tenant = tenantRepository.save(tenant);

        invoice = new Invoice();
        invoice.setTenantId(tenant.getId());
        invoice.setInvoiceNumber("TEMP-" + UUID.randomUUID());
        invoice.setTotalAmount(new BigDecimal("0.00"));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setSubmitterId(UUID.randomUUID());
        
        // Set TenantContext for the test so that repository reads are not blocked by TenantFilterAspect
        TenantContext.setCurrentTenant(tenant.getId());
        
        invoice = invoiceRepository.save(invoice);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private byte[] createMockInvoiceImage() throws Exception {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 800, 600);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.PLAIN, 24));
        g2d.drawString("Invoice Number: REAL-456", 50, 100);
        g2d.drawString("Total: 1234.56", 50, 150);
        g2d.drawString("Date: 2026-09-30", 50, 200);
        g2d.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void testRealOcrPipeline() throws Exception {
        byte[] imageBytes = createMockInvoiceImage();
        
        org.springframework.mock.web.MockMultipartFile file = new org.springframework.mock.web.MockMultipartFile(
                "file",
                "invoice.png",
                "image/png",
                imageBytes
        );

        // Upload
        Document document = documentService.uploadDocument(file, invoice.getId(), tenant.getId());
        
        // Wait for JobRunr to process it (or manually trigger processExtractionJob for testing syncly)
        // Since JobRunr relies on the transaction commit in a different context, let's just trigger it directly to ensure we don't have async test flakes
        extractionJobService.processExtractionJob(document.getId(), tenant.getId());

        // Verify Results
        Document updatedDoc = documentRepository.findById(document.getId()).get();
        assertThat(updatedDoc.getStatus()).isEqualTo(DocumentStatus.EXTRACTION_COMPLETED);

        ExtractionResult result = extractionResultRepository.findByDocumentIdAndTenantId(document.getId(), tenant.getId()).get();
        assertThat(result.getStatus()).isEqualTo(ExtractionStatus.COMPLETED);
        
        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).get();
        
        // Since we provided an image with "Invoice Number: REAL-456", the real OCR should extract it.
        assertThat(updatedInvoice.getInvoiceNumber()).isEqualTo("REAL-456");
        assertThat(updatedInvoice.getTotalAmount()).isEqualByComparingTo("1234.56");
        
        assertThat(result.getExtractedData()).containsKey("invoice_number");
        assertThat(result.getConfidenceScores()).isNotEmpty();
    }
}
