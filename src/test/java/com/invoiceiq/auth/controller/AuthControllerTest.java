package com.invoiceiq.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.auth.dto.LoginRequest;
import com.invoiceiq.auth.entity.Role;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.UserRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SuppressWarnings("null")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    protected User testUser;
    protected Tenant testTenant;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());
        tenant.setName("Test Tenant");
        tenant = tenantRepository.save(tenant);
        testTenant = tenant;

        testUser = new User();
        testUser.setId(UUID.randomUUID());
        testUser.setTenantId(tenant.getId());
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash(passwordEncoder.encode("password"));
        testUser.setRole(Role.ADMIN);
        testUser = userRepository.save(testUser);
    }

    @Test
    void testSuccessfulLogin() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "password", testTenant.getId());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void testInvalidPassword() throws Exception {
        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword", testTenant.getId());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized()); // Or 403 depending on Spring Security mapping
    }

    @Test
    void testNonexistentUser() throws Exception {
        LoginRequest request = new LoginRequest("nobody@example.com", "password", testTenant.getId());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized()); // Or 403
    }
}
