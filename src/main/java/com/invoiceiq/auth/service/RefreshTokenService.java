package com.invoiceiq.auth.service;

import com.invoiceiq.auth.entity.RefreshToken;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import io.jsonwebtoken.JwtException;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${application.security.jwt.refresh-token.expiration}")
    private long refreshTokenDurationMs;

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }

    public RefreshToken createRefreshToken(User user, String tokenValue) {
        String hashedToken = hashToken(tokenValue);
        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .token(hashedToken)
                .expiryDate(Instant.now().plusMillis(refreshTokenDurationMs))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new JwtException("Refresh token was expired. Please make a new signin request");
        }
        if (token.isRevoked()) {
            throw new JwtException("Refresh token is revoked.");
        }
        return token;
    }
    
    public void revokeAllUserTokens(User user) {
        var validUserTokens = refreshTokenRepository.findAllByUserIdAndRevokedFalse(user.getId());
        if (validUserTokens.isEmpty())
            return;
        validUserTokens.forEach(token -> {
            token.setRevoked(true);
        });
        refreshTokenRepository.saveAll(validUserTokens);
    }
}
