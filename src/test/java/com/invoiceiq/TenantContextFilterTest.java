package com.invoiceiq;

import com.invoiceiq.tenant.context.TenantContext;
import com.invoiceiq.tenant.context.TenantContextFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;


import static org.junit.jupiter.api.Assertions.assertNull;

@SuppressWarnings("null")
public class TenantContextFilterTest {

    @Test
    void testTenantContextCleanup() throws ServletException, IOException {
        TenantContextFilter filter = new TenantContextFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID tenantId = UUID.randomUUID();
        request.addHeader("X-Tenant-ID", tenantId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        
        FilterChain filterChain = (req, res) -> {
            // Simulate another filter setting the TenantContext (e.g. JwtAuthenticationFilter)
            TenantContext.setCurrentTenant(tenantId);
        };

        filter.doFilter(request, response, filterChain);

        // Context MUST be cleared after the filter chain completes
        assertNull(TenantContext.getCurrentTenant(), "TenantContext was not cleared!");
    }
}
