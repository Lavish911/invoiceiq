package com.invoiceiq.auth.service;

import com.invoiceiq.auth.dto.AuthResponse;
import com.invoiceiq.auth.dto.LoginRequest;
import com.invoiceiq.auth.dto.RefreshTokenRequest;
import com.invoiceiq.auth.entity.RefreshToken;

import com.invoiceiq.auth.repository.RefreshTokenRepository;
import com.invoiceiq.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AuthenticationService {

    private final UserRepository repository;
    private final JwtService jwtService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthResponse authenticate(LoginRequest request) {
        var user = repository.findByEmailAndTenantId(request.getEmail(), request.getTenantId())
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException("Invalid credentials"));
                
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new org.springframework.security.authentication.BadCredentialsException("Invalid credentials");
        }
        
        var jwtToken = jwtService.generateToken(user);
        var refreshTokenValue = jwtService.generateRefreshToken(user);
        
        refreshTokenService.revokeAllUserTokens(user);
        refreshTokenService.createRefreshToken(user, refreshTokenValue);

        return AuthResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshTokenValue)
                .build();
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String token = request.getRefreshToken();
        
        String tokenType = jwtService.extractTokenType(token);
        if (!"refresh".equals(tokenType)) {
            throw new io.jsonwebtoken.JwtException("Invalid token type for refresh");
        }
        
        String hashedToken = RefreshTokenService.hashToken(token);
        
        return refreshTokenRepository.findByToken(hashedToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUserId)
                .flatMap(repository::findById)
                .map(user -> {
                    String accessToken = jwtService.generateToken(user);
                    String newRefreshTokenValue = jwtService.generateRefreshToken(user);
                    
                    // Rotate refresh token
                    refreshTokenService.revokeAllUserTokens(user);
                    refreshTokenService.createRefreshToken(user, newRefreshTokenValue);

                    return AuthResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(newRefreshTokenValue)
                            .build();
                })
                .orElseThrow(() -> new io.jsonwebtoken.JwtException("Refresh token is not in database!"));
    }
}
