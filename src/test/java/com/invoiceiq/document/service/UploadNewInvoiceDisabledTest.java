package com.invoiceiq.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.auth.dto.AuthResponse;
import com.invoiceiq.auth.dto.LoginRequest;
import com.invoiceiq.auth.entity.Role;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.UserRepository;
import com.invoiceiq.document.repository.DocumentRepository;
import com.invoiceiq.document.repository.ExtractionResultRepository;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import com.invoiceiq.tenant.entity.Tenant;
import com.invoiceiq.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Without {@code app.upload-new.enabled} the route behaves as absent (404).
 * No TestPropertySource here: the default must stay disabled.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadNewInvoiceDisabledTest {

    @Autowired
    private MockMvc mockMvc;

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        extractionResultRepository.deleteAll();
        documentRepository.deleteAll();
        invoiceRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = new Tenant();
        tenant.setName("Disabled Tenant " + UUID.randomUUID());
        tenant = tenantRepository.save(tenant);

        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setTenantId(tenant.getId());
        admin.setEmail("admin@example.com");
        admin.setPasswordHash(passwordEncoder.encode("password"));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);

        LoginRequest request = new LoginRequest("admin@example.com", "password", tenant.getId());
        String responseStr = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        adminToken = objectMapper.readValue(responseStr, AuthResponse.class).getAccessToken();
    }

    @Test
    void disabledRouteReturns404() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "inv.pdf", MediaType.APPLICATION_PDF_VALUE, "dummy".getBytes());
        mockMvc.perform(multipart(HttpMethod.POST, "/api/invoices/upload-new")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }
}
