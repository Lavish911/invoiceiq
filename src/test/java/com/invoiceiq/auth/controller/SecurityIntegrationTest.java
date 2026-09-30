package com.invoiceiq.auth.controller; // Force IDE Sync

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.auth.dto.AuthResponse;
import com.invoiceiq.auth.dto.LoginRequest;
import com.invoiceiq.auth.entity.Role;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.UserRepository;
import com.invoiceiq.tenant.entity.Tenant;
import com.invoiceiq.tenant.repository.TenantRepository;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("null")
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;
    private Tenant tenantA;
    private Tenant tenantB;
    private Invoice invoiceA;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("DELETE FROM extraction_result");
        jdbcTemplate.execute("DELETE FROM document");
        invoiceRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        tenantA = new Tenant();
        tenantA.setId(UUID.randomUUID());
        tenantA.setName("Tenant A");
        tenantA = tenantRepository.save(tenantA);

        tenantB = new Tenant();
        tenantB.setId(UUID.randomUUID());
        tenantB.setName("Tenant B");
        tenantB = tenantRepository.save(tenantB);

        userA = new User();
        userA.setId(UUID.randomUUID());
        userA.setTenantId(tenantA.getId());
        userA.setEmail("usera@example.com");
        userA.setPasswordHash(passwordEncoder.encode("password"));
        userA.setRole(Role.ADMIN);
        userA = userRepository.save(userA);

        userB = new User();
        userB.setId(UUID.randomUUID());
        userB.setTenantId(tenantB.getId());
        userB.setEmail("userb@example.com");
        userB.setPasswordHash(passwordEncoder.encode("password"));
        userB.setRole(Role.ADMIN);
        userB = userRepository.save(userB);

        invoiceA = new Invoice();
        invoiceA.setId(UUID.randomUUID());
        invoiceA.setTenantId(tenantA.getId());
        invoiceA.setSubmitterId(userA.getId());
        invoiceA.setInvoiceNumber("INV-A");
        invoiceA.setTotalAmount(new BigDecimal("100.00"));
        invoiceA.setStatus(InvoiceStatus.DRAFT);
        invoiceA = invoiceRepository.save(invoiceA);

        tokenA = getAccessToken("usera@example.com", "password", tenantA.getId());
        tokenB = getAccessToken("userb@example.com", "password", tenantB.getId());
    }

    private String getAccessToken(String email, String password, UUID tenantId) throws Exception {
        LoginRequest request = new LoginRequest(email, password, tenantId);
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, AuthResponse.class).getAccessToken();
    }

    @Test
    void testTenantIsolation_UserACanAccessInvoiceA() throws Exception {
        mockMvc.perform(get("/api/invoices") // Assuming GET /api/invoices returns invoices
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].invoiceNumber").value("INV-A"));
    }

    @Test
    void testTenantIsolation_UserBCannotAccessInvoiceA() throws Exception {
        mockMvc.perform(get("/api/invoices")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }
    
    @Test
    void testSpoofedTenantHeaderIsIgnored() throws Exception {
        // User B tries to spoof Tenant A's ID
        mockMvc.perform(get("/api/invoices")
                .header("Authorization", "Bearer " + tokenB)
                .header("X-Tenant-ID", tenantA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void testAdminCanDeleteInvoice() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/invoices/" + invoiceA.getId())
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());
    }

    @Test
    void testSubmitterCannotDeleteInvoice() throws Exception {
        User submitter = new User();
        submitter.setId(UUID.randomUUID());
        submitter.setTenantId(tenantA.getId());
        submitter.setEmail("submitter@example.com");
        submitter.setPasswordHash(passwordEncoder.encode("password"));
        submitter.setRole(Role.SUBMITTER);
        userRepository.save(submitter);

        String submitterToken = getAccessToken("submitter@example.com", "password", tenantA.getId());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/invoices/" + invoiceA.getId())
                .header("Authorization", "Bearer " + submitterToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDuplicateEmailAcrossTenantsLoginSuccessfully() throws Exception {
        User duplicate = new User();
        duplicate.setId(UUID.randomUUID());
        duplicate.setTenantId(tenantB.getId());
        duplicate.setEmail("usera@example.com"); // Same email as userA, but in tenantB
        duplicate.setPasswordHash(passwordEncoder.encode("password"));
        duplicate.setRole(Role.ADMIN);
        userRepository.save(duplicate);

        String token = getAccessToken("usera@example.com", "password", tenantB.getId());
        org.junit.jupiter.api.Assertions.assertNotNull(token);
    }
}
