package com.invoiceiq.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.audit.repository.AuditLogRepository;
import com.invoiceiq.auth.dto.AuthResponse;
import com.invoiceiq.auth.dto.LoginRequest;
import com.invoiceiq.auth.entity.Role;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.UserRepository;
import com.invoiceiq.document.entity.Document;
import com.invoiceiq.document.repository.DocumentRepository;
import com.invoiceiq.document.repository.ExtractionResultRepository;
import com.invoiceiq.document.service.UploadNewInvoiceService.UploadNewResult;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import com.invoiceiq.tenant.context.TenantContext;
import com.invoiceiq.tenant.entity.Tenant;
import com.invoiceiq.tenant.repository.TenantRepository;
import com.invoiceiq.vendor.entity.Vendor;
import com.invoiceiq.vendor.repository.VendorRepository;
import com.invoiceiq.vendor.service.VendorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"app.upload-new.enabled=true"})
class UploadNewInvoiceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UploadNewInvoiceService uploadNewInvoiceService;

    @Autowired
    private ExtractionJobService extractionJobService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ExtractionResultRepository extractionResultRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PythonExtractionClient pythonExtractionClient;

    @MockBean
    private DocumentStorageService documentStorageService;

    private Tenant tenant;
    private String adminToken;
    private String submitterToken;
    private String approverToken;

    @BeforeEach
    void setUp() throws Exception {
        // Plain repository deleteAll() is unreliable here: the fail-closed
        // tenant filter silently scopes bulk operations when no TenantContext
        // is bound, leaving cross-test rows behind. Raw deletes bypass the
        // filter deterministically (FK order, children first).
        jdbcTemplate.execute("DELETE FROM extraction_result");
        jdbcTemplate.execute("DELETE FROM document");
        jdbcTemplate.execute("DELETE FROM invoice_line_item");
        jdbcTemplate.execute("DELETE FROM approval_step");
        jdbcTemplate.execute("DELETE FROM workflow_instance");
        jdbcTemplate.execute("DELETE FROM notification");
        jdbcTemplate.execute("DELETE FROM audit_log");
        jdbcTemplate.execute("DELETE FROM invoice");
        jdbcTemplate.execute("DELETE FROM vendor");
        jdbcTemplate.execute("DELETE FROM users");
        jdbcTemplate.execute("DELETE FROM refresh_token");
        jdbcTemplate.execute("DELETE FROM tenant");

        tenant = new Tenant();

        tenant = new Tenant();
        tenant.setName("UploadNew Tenant " + UUID.randomUUID());
        tenant = tenantRepository.save(tenant);

        adminToken = loginAs("boss@example.com", Role.ADMIN);
        submitterToken = loginAs("sub@example.com", Role.SUBMITTER);
        approverToken = loginAs("appr@example.com", Role.APPROVER);

        when(documentStorageService.storeFile(any(), anyString()))
                .thenAnswer(invocation -> "upload-new/" + UUID.randomUUID());
        when(documentStorageService.getFile(anyString()))
                .thenReturn("dummy content".getBytes());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private String loginAs(String email, Role role) throws Exception {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setRole(role);
        userRepository.save(user);

        LoginRequest request = new LoginRequest(email, "password", tenant.getId());
        String responseStr = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(responseStr, AuthResponse.class).getAccessToken();
    }

    private MockMultipartFile pdf(String name, byte[] bytes) {
        return new MockMultipartFile("file", name, MediaType.APPLICATION_PDF_VALUE, bytes);
    }

    /**
     * Reads the vendor without tripping lazy loading: the invoice's vendor
     * association is LAZY and the test has no open session, so resolve
     * through the repository (identifier access on the proxy is session-free).
     */
    private Vendor vendorOf(Invoice invoice) {
        TenantContext.setCurrentTenant(invoice.getTenantId());
        try {
            return vendorRepository.findById(invoice.getVendor().getId()).orElseThrow();
        } finally {
            TenantContext.clear();
        }
    }

    private String vendorNameOf(Invoice invoice) {
        return vendorOf(invoice).getName();
    }

    private UploadNewResult uploadNew(byte[] bytes, String email) {
        TenantContext.setCurrentTenant(tenant.getId());
        try {
            return uploadNewInvoiceService.uploadNew(pdf("inv.pdf", bytes), email, tenant.getId());
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void placeholderCreatedOnFirstUpload() {
        UploadNewResult result = uploadNew("first-file".getBytes(), "sub@example.com");
        assertNotNull(result.invoiceId);
        Invoice invoice = invoiceRepository.findById(result.invoiceId).orElseThrow();
        assertEquals(VendorService.UNASSIGNED_VENDOR_NAME, vendorNameOf(invoice));
        assertEquals(tenant.getId(), vendorOf(invoice).getTenantId());
    }

    @Test
    void placeholderReusedAcrossUploads() {
        uploadNew("file-a".getBytes(), "sub@example.com");
        uploadNew("file-b".getBytes(), "sub@example.com");
        assertEquals(1, vendorRepository.findByTenantIdOrderByName(tenant.getId()).size());
    }

    @Test
    void concurrentPlaceholderCreationStaysSingleRow() throws Exception {
        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                TenantContext.setCurrentTenant(tenant.getId());
                try {
                    uploadNewInvoiceService.uploadNew(
                            pdf("race" + idx + ".pdf", ("race-bytes-" + idx).getBytes()),
                            "sub@example.com", tenant.getId());
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
        assertEquals(1, vendorRepository.findByTenantIdOrderByName(tenant.getId()).size());
    }

    @Test
    void sameFileReplayReturnsSameInvoice() {
        byte[] bytes = "identical-bytes".getBytes();
        UploadNewResult first = uploadNew(bytes, "sub@example.com");
        UploadNewResult second = uploadNew(bytes, "sub@example.com");
        assertEquals(first.invoiceId, second.invoiceId);
        assertEquals(first.documentId, second.documentId);
        assertTrue(second.replayed);
        assertEquals(1, invoiceRepository.findAll().size());
    }

    @Test
    void conflictingIdempotencyPayloadReturns409() throws Exception {
        byte[] bytes = "conflict-bytes".getBytes();
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        String hash = java.util.HexFormat.of().formatHex(digest.digest(bytes));

        TenantContext.setCurrentTenant(tenant.getId());
        Invoice clash = new Invoice();
        clash.setTenantId(tenant.getId());
        clash.setInvoiceNumber("CLASH-" + UUID.randomUUID());
        clash.setTotalAmount(new BigDecimal("1.00"));
        clash.setStatus(InvoiceStatus.DRAFT);
        clash.setSubmitterId(UUID.randomUUID());
        clash.setIdempotencyKey("sha256:" + hash);
        clash.setRequestHash("different-payload");
        try {
            Vendor vendor = new Vendor();
            vendor.setTenantId(tenant.getId());
            vendor.setName("Clash Vendor");
            vendor = vendorRepository.save(vendor);
            clash.setVendor(vendor);
            invoiceRepository.save(clash);
        } finally {
            TenantContext.clear();
        }

        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/upload-new")
                        .file(pdf("inv.pdf", bytes))
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isConflict());
    }

    @Test
    void differentFileCreatesNewInvoice() {
        UploadNewResult first = uploadNew("bytes-one".getBytes(), "sub@example.com");
        UploadNewResult second = uploadNew("bytes-two".getBytes(), "sub@example.com");
        assertNotEquals(first.invoiceId, second.invoiceId);
    }

    @Test
    void tenantIsolationHoldsForUploadNew() throws Exception {
        Tenant other = new Tenant();
        other.setName("Foreign Tenant " + UUID.randomUUID());
        other = tenantRepository.save(other);

        User foreign = new User();
        foreign.setId(UUID.randomUUID());
        foreign.setTenantId(other.getId());
        foreign.setEmail("foreign@example.com");
        foreign.setPasswordHash(passwordEncoder.encode("password"));
        foreign.setRole(Role.SUBMITTER);
        userRepository.save(foreign);

        LoginRequest request = new LoginRequest("foreign@example.com", "password", other.getId());
        String responseStr = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String foreignToken = objectMapper.readValue(responseStr, AuthResponse.class).getAccessToken();

        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/upload-new")
                        .file(pdf("inv.pdf", "foreign-bytes".getBytes()))
                        .header("Authorization", "Bearer " + foreignToken))
                .andExpect(status().isAccepted());

        // Foreign invoice invisible to the demo tenant, and each tenant owns its placeholder.
        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/upload-new")
                        .file(pdf("inv.pdf", "foreign-bytes".getBytes()))
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isAccepted());
        assertEquals(1, vendorRepository.findByTenantIdOrderByName(tenant.getId()).size());
        assertEquals(1, vendorRepository.findByTenantIdOrderByName(other.getId()).size());
    }

    @Test
    void rbacAllowsAdminAndSubmitter() throws Exception {
        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/upload-new")
                        .file(pdf("a.pdf", "rbac-a".getBytes()))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted());
        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/upload-new")
                        .file(pdf("b.pdf", "rbac-b".getBytes()))
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isAccepted());
    }

    @Test
    void rbacRejectsApproverAndAnonymous() throws Exception {
        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/upload-new")
                        .file(pdf("a.pdf", "rbac-c".getBytes()))
                        .header("Authorization", "Bearer " + approverToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/upload-new")
                        .file(pdf("a.pdf", "rbac-d".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oversizedAndInvalidFilesRejected() throws Exception {
        byte[] big = new byte[11 * 1024 * 1024];
        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/upload-new")
                        .file(pdf("big.pdf", big))
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/upload-new")
                        .file(new MockMultipartFile("file", "evil.exe",
                                MediaType.APPLICATION_OCTET_STREAM_VALUE, "MZ".getBytes()))
                        .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void vendorUniqueConstraintExists() {
        TenantContext.setCurrentTenant(tenant.getId());
        try {
            Vendor first = new Vendor();
            first.setTenantId(tenant.getId());
            first.setName("Dup Vendor");
            vendorRepository.saveAndFlush(first);
            Vendor second = new Vendor();
            second.setTenantId(tenant.getId());
            second.setName("Dup Vendor");
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> vendorRepository.saveAndFlush(second));
        } finally {
            TenantContext.clear();
        }
    }

    private void mockOcr(String number, String total, String vendorName) {
        com.invoiceiq.document.dto.ExtractionResponse response =
                new com.invoiceiq.document.dto.ExtractionResponse();
        response.setStatus("success");
        response.setExtractedData(new java.util.HashMap<>(java.util.Map.of(
                "invoice_number", number,
                "total_amount", total,
                "vendor_name", vendorName)));
        when(pythonExtractionClient.extractInvoiceData(any(), anyString(), anyString()))
                .thenReturn(response);
    }

    private Document uploadAndExtract(String fileBytes, String number, String total, String vendorName) {
        UploadNewResult result = uploadNew(fileBytes.getBytes(), "sub@example.com");
        Document document = documentRepository.findById(result.documentId).orElseThrow();
        TenantContext.setCurrentTenant(tenant.getId());
        try {
            extractionJobService.processExtractionJob(document.getId(), tenant.getId());
        } finally {
            TenantContext.clear();
        }
        return documentRepository.findById(document.getId()).orElseThrow();
    }

    @Test
    void ocrCompletedFlowWritesBackFields() {
        mockOcr("OCR-100", "250.00", "Some Vendor");
        Document document = uploadAndExtract("ocr-flow", "OCR-100", "250.00", "Some Vendor");
        assertEquals(com.invoiceiq.document.entity.DocumentStatus.EXTRACTION_COMPLETED, document.getStatus());
        Invoice reloaded = invoiceRepository.findById(
                document.getInvoice().getId()).orElseThrow();
        assertEquals("OCR-100", reloaded.getInvoiceNumber());
        assertEquals(0, new BigDecimal("250.00").compareTo(reloaded.getTotalAmount()));
    }

    @Test
    void exactVendorMatchReassignsAndAudits() {
        TenantContext.setCurrentTenant(tenant.getId());
        Vendor real;
        try {
            real = new Vendor();
            real.setTenantId(tenant.getId());
            real.setName("Acme Office");
            real = vendorRepository.save(real);
        } finally {
            TenantContext.clear();
        }
        mockOcr("OCR-200", "99.00", "Acme Office");
        Document document = uploadAndExtract("ocr-match", "OCR-200", "99.00", "Acme Office");
        Invoice reloaded = invoiceRepository.findById(
                document.getInvoice().getId()).orElseThrow();
        assertEquals(real.getId(), reloaded.getVendor().getId());
        assertTrue(auditLogRepository
                .findByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDesc(
                        "INVOICE", reloaded.getId(), tenant.getId())
                .stream().anyMatch(e -> "VENDOR_REASSIGNED".equals(e.getAction())));
    }

    @Test
    void vendorMatchIsCaseInsensitive() {
        TenantContext.setCurrentTenant(tenant.getId());
        try {
            Vendor real = new Vendor();
            real.setTenantId(tenant.getId());
            real.setName("Acme Office");
            vendorRepository.save(real);
        } finally {
            TenantContext.clear();
        }
        mockOcr("OCR-300", "10.00", "aCmE oFfIcE");
        Document document = uploadAndExtract("ocr-ci", "OCR-300", "10.00", "aCmE oFfIcE");
        Invoice reloaded = invoiceRepository.findById(
                document.getInvoice().getId()).orElseThrow();
        assertEquals("Acme Office", vendorNameOf(reloaded));
    }

    @Test
    void noVendorMatchRetainsPlaceholder() {
        mockOcr("OCR-400", "10.00", "Nobody Everheard");
        Document document = uploadAndExtract("ocr-nomatch", "OCR-400", "10.00", "Nobody Everheard");
        Invoice reloaded = invoiceRepository.findById(
                document.getInvoice().getId()).orElseThrow();
        assertEquals(VendorService.UNASSIGNED_VENDOR_NAME, vendorNameOf(reloaded));
        assertTrue(auditLogRepository
                .findByEntityTypeAndEntityIdAndTenantIdOrderByCreatedAtDesc(
                        "INVOICE", reloaded.getId(), tenant.getId())
                .stream().noneMatch(e -> "VENDOR_REASSIGNED".equals(e.getAction())));
    }

    @Test
    void placeholderNameItselfIsNeverMatched() {
        mockOcr("OCR-500", "10.00", "uNaSsIgNeD vEnDoR");
        Document document = uploadAndExtract("ocr-ph", "OCR-500", "10.00", "uNaSsIgNeD vEnDoR");
        Invoice reloaded = invoiceRepository.findById(
                document.getInvoice().getId()).orElseThrow();
        assertEquals(VendorService.UNASSIGNED_VENDOR_NAME, vendorNameOf(reloaded));
    }

    @Test
    void optimisticLockingVersionAdvancesOnWriteback() {
        mockOcr("OCR-600", "10.00", "Nobody");
        UploadNewResult result = uploadNew("ocr-lock".getBytes(), "sub@example.com");
        Invoice before = invoiceRepository.findById(result.invoiceId).orElseThrow();
        long versionBefore = before.getVersion();
        TenantContext.setCurrentTenant(tenant.getId());
        try {
            extractionJobService.processExtractionJob(result.documentId, tenant.getId());
        } finally {
            TenantContext.clear();
        }
        Invoice after = invoiceRepository.findById(result.invoiceId).orElseThrow();
        assertTrue(after.getVersion() > versionBefore,
                "writeback must go through @Version so races collapse to 409");
        assertEquals("OCR-600", after.getInvoiceNumber());
    }
}
