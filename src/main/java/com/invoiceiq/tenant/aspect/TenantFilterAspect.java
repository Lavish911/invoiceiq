package com.invoiceiq.tenant.aspect;

import com.invoiceiq.tenant.context.TenantContext;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class TenantFilterAspect {

    private final EntityManager entityManager;

    public TenantFilterAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Before("execution(* org.springframework.data.repository.Repository+.*(..)) && " +
            "!execution(* com.invoiceiq.auth.repository.UserRepository.*(..)) && " +
            "!execution(* com.invoiceiq.auth.repository.RefreshTokenRepository.*(..)) && " +
            "!execution(* com.invoiceiq.tenant.repository.TenantRepository.*(..))")
    public void enableTenantFilter() {
        Session session = entityManager.unwrap(Session.class);
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId != null) {
            session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
        } else {
            // Fail closed: if no tenant context exists, block access to tenant data
            session.enableFilter("tenantFilter").setParameter("tenantId", UUID.fromString("00000000-0000-0000-0000-000000000000"));
        }
    }
}
