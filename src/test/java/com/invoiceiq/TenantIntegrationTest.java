package com.invoiceiq;

import com.invoiceiq.tenant.entity.Tenant;
import com.invoiceiq.tenant.service.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
public class TenantIntegrationTest {

    @Autowired
    private TenantService tenantService;

    @Test
    void testCreateAndRetrieveTenant() {
        // Arrange
        String tenantName = "Acme Corp";

        // Act
        Tenant savedTenant = tenantService.createTenant(tenantName);

        // Assert
        assertNotNull(savedTenant.getId());
        assertThat(savedTenant.getName()).isEqualTo(tenantName);

        // Act 2
        Tenant retrievedTenant = tenantService.getTenant(java.util.Objects.requireNonNull(savedTenant.getId()));

        // Assert 2
        assertThat(retrievedTenant.getId()).isEqualTo(savedTenant.getId());
        assertThat(retrievedTenant.getName()).isEqualTo(tenantName);
        assertNotNull(retrievedTenant.getCreatedAt());
        assertNotNull(retrievedTenant.getUpdatedAt());
    }
}
