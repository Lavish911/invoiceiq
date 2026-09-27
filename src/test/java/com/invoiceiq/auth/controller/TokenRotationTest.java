package com.invoiceiq.auth.controller; // Trigger IDE sync

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.auth.dto.AuthResponse;
import com.invoiceiq.auth.dto.LoginRequest;
import com.invoiceiq.auth.dto.RefreshTokenRequest;
import com.invoiceiq.auth.entity.RefreshToken;
import com.invoiceiq.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SuppressWarnings("null")
public class TokenRotationTest extends AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void testRefreshTokenRotation() throws Exception {
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password", testTenant.getId());
        
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(response, AuthResponse.class);
        String oldRefreshToken = authResponse.getRefreshToken();

        // Rotate
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(oldRefreshToken);
        String refreshResponseStr = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        objectMapper.readValue(refreshResponseStr, AuthResponse.class);
        
        // Old token should be revoked
        String hashedOldToken = com.invoiceiq.auth.service.RefreshTokenService.hashToken(oldRefreshToken);
        RefreshToken oldTokenEntity = refreshTokenRepository.findByToken(hashedOldToken).orElseThrow();
        assert oldTokenEntity.isRevoked();

        // Reusing revoked token should fail (Wait, currently it just verifies revoked=true but returns 500)
        // Need to ensure it returns 401 or 400.
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRefreshTokenCannotBeUsedAsAccessToken() throws Exception {
        LoginRequest loginRequest = new LoginRequest("test@example.com", "password", testTenant.getId());
        
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(response, AuthResponse.class);
        String refreshToken = authResponse.getRefreshToken();

        // Using refresh token to access a protected endpoint
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/invoices")
                .header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }
}
