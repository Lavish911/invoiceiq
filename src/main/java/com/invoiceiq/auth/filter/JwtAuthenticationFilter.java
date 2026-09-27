package com.invoiceiq.auth.filter;

import com.invoiceiq.auth.service.JwtService;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.tenant.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.IOException;
import java.util.UUID;

import com.invoiceiq.auth.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    
    @Qualifier("handlerExceptionResolver")
    private final HandlerExceptionResolver handlerExceptionResolver;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;
        final String tenantIdStr;

        try {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                filterChain.doFilter(request, response);
                return;
            }
            
            jwt = authHeader.substring(7);
            userEmail = jwtService.extractUsername(jwt);
            tenantIdStr = jwtService.extractTenantId(jwt);

            if (userEmail != null && tenantIdStr != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                User user = userRepository.findByEmailAndTenantId(userEmail, UUID.fromString(tenantIdStr))
                        .orElseThrow(() -> new io.jsonwebtoken.JwtException("User not found or tenant mismatch"));
                
                UserDetails userDetails = user;
                
                if (jwtService.isTokenValid(jwt, userDetails)) {
                    
                    String tokenType = jwtService.extractTokenType(jwt);
                    if (!"access".equals(tokenType)) {
                        throw new io.jsonwebtoken.JwtException("Invalid token type");
                    }
                
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    if (tenantIdStr != null) {
                        TenantContext.setCurrentTenant(UUID.fromString(tenantIdStr));
                    }
                }
            }
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            handlerExceptionResolver.resolveException(request, response, null, ex);
        }
    }
}
