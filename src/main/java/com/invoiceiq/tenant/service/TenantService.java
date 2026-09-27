package com.invoiceiq.tenant.service;

import com.invoiceiq.tenant.entity.Tenant;
import com.invoiceiq.common.exception.ResourceNotFoundException;
import com.invoiceiq.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TenantService {

    private final TenantRepository tenantRepository;

    @Transactional
    public Tenant createTenant(String name) {
        Tenant tenant = new Tenant();
        tenant.setName(name);
        return tenantRepository.save(tenant);
    }

    @Transactional(readOnly = true)
    public Tenant getTenant(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
    }
}
