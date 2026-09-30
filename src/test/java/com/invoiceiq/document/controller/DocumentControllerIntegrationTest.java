package com.invoiceiq.document.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.auth.dto.AuthResponse;
import com.invoiceiq.auth.dto.LoginRequest;
import com.invoiceiq.auth.entity.Role;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.UserRepository;
import com.invoiceiq.document.entity.Document;
import com.invoiceiq.document.entity.DocumentStatus;
import com.invoiceiq.document.repository.DocumentRepository;
import com.invoiceiq.document.service.ExtractionJobService;
import com.invoiceiq.document.service.PythonExtractionClient;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import com.invoiceiq.tenant.entity.Tenant;
import com.invoiceiq.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public class DocumentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private DocumentRepository documentRepository;



    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PythonExtractionClient pythonExtractionClient; // Mock this since we don't spin up FastAPI in this test

    private Tenant tenant;
    private Invoice invoice;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
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

        User adminUser = new User();
        adminUser.setId(UUID.randomUUID());
        adminUser.setTenantId(tenant.getId());
        adminUser.setEmail("admin@example.com");
        adminUser.setPasswordHash(passwordEncoder.encode("password"));
        adminUser.setRole(Role.ADMIN);
        userRepository.save(adminUser);

        LoginRequest request = new LoginRequest("admin@example.com", "password", tenant.getId());
        String responseStr = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(responseStr, AuthResponse.class);
        adminToken = authResponse.getAccessToken();
    }

    @Test
    void testUploadDocumentSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "invoice.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "dummy content".getBytes()
        );

        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/" + invoice.getId() + "/documents")
                        .file(file)
                        .header("X-Tenant-ID", tenant.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value(DocumentStatus.UPLOADED.name()))
                .andExpect(jsonPath("$.message").exists());

        assertThat(documentRepository.findAll()).hasSize(1);
        Document savedDoc = documentRepository.findAll().get(0);
        assertThat(savedDoc.getOriginalFilename()).isEqualTo("invoice.pdf");
        assertThat(savedDoc.getTenantId()).isEqualTo(tenant.getId());
        
        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).get();
        assertThat(updatedInvoice.getS3Key()).isNotNull();
    }

    @Test
    void testUploadInvalidFileType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/x-msdownload",
                "dummy content".getBytes()
        );

        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/" + invoice.getId() + "/documents")
                        .file(file)
                        .header("X-Tenant-ID", tenant.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPathTraversalAttemptBlocked() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../../etc/passwd.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "dummy content".getBytes()
        );

        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/" + invoice.getId() + "/documents")
                        .file(file)
                        .header("X-Tenant-ID", tenant.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isAccepted());

        Document savedDoc = documentRepository.findAll().get(0);
        // The service logic should sanitize the filename
        assertThat(savedDoc.getOriginalFilename()).isEqualTo("passwd.pdf");
        assertThat(savedDoc.getStorageKey()).doesNotContain("..");
    }

    @Test
    void testFetchingProcessingStatus() throws Exception {
        Document document = new Document();
        document.setTenantId(tenant.getId());
        document.setInvoice(invoice);
        document.setOriginalFilename("test.pdf");
        document.setContentType("application/pdf");
        document.setFileSize(1024L);
        document.setStorageKey(tenant.getId() + "/test.pdf");
        document.setChecksum("checksum");
        document.setStatus(DocumentStatus.PROCESSING);
        document = documentRepository.save(document);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/invoices/" + invoice.getId() + "/documents/" + document.getId())
                        .header("X-Tenant-ID", tenant.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void testUploadOversizedFile() throws Exception {
        byte[] largeContent = new byte[10 * 1024 * 1024 + 1]; // 10MB + 1 byte
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                largeContent
        );

        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/" + invoice.getId() + "/documents")
                        .file(file)
                        .header("X-Tenant-ID", tenant.getId().toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest()); // Handled by GlobalExceptionHandler for IllegalArgumentException
    }
}
