package com.invoiceiq.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.auth.dto.AuthResponse;
import com.invoiceiq.auth.dto.LoginRequest;
import com.invoiceiq.auth.entity.Role;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.UserRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.demo.tenant-id=99999999-9999-9999-9999-999999999999"
})
class DemoTenantIsolationTest {

    private static final UUID DEMO_TENANT_ID =
            UUID.fromString("99999999-9999-9999-9999-999999999999");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private String demoToken;
    private Invoice foreignInvoice;

    @BeforeEach
    void setUp() throws Exception {
        invoiceRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant demoTenant = new Tenant();
        demoTenant.setId(DEMO_TENANT_ID);
        demoTenant.setName("Isolation Demo Tenant");
        tenantRepository.save(demoTenant);

        User demoUser = new User();
        demoUser.setId(UUID.randomUUID());
        demoUser.setTenantId(DEMO_TENANT_ID);
        demoUser.setEmail("demo@invoiceiq.demo");
        demoUser.setPasswordHash(passwordEncoder.encode("demo-password"));
        demoUser.setRole(Role.SUBMITTER);
        userRepository.save(demoUser);

        Tenant foreignTenant = new Tenant();
        foreignTenant.setName("Foreign Tenant " + UUID.randomUUID());
        foreignTenant = tenantRepository.save(foreignTenant);

        foreignInvoice = new Invoice();
        foreignInvoice.setTenantId(foreignTenant.getId());
        foreignInvoice.setInvoiceNumber("FOREIGN-" + UUID.randomUUID());
        foreignInvoice.setTotalAmount(new BigDecimal("10.00"));
        foreignInvoice.setStatus(InvoiceStatus.DRAFT);
        foreignInvoice.setSubmitterId(UUID.randomUUID());
        foreignInvoice = invoiceRepository.save(foreignInvoice);

        LoginRequest request = new LoginRequest("demo@invoiceiq.demo", "demo-password", DEMO_TENANT_ID);
        String responseStr = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        demoToken = objectMapper.readValue(responseStr, AuthResponse.class).getAccessToken();
    }

    @Test
    void demoUserCannotReadForeignTenantInvoice() throws Exception {
        mockMvc.perform(get("/api/invoices/" + foreignInvoice.getId())
                        .header("Authorization", "Bearer " + demoToken))
                .andExpect(status().isNotFound());
    }
}
