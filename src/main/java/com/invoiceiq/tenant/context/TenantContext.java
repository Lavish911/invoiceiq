package com.invoiceiq.tenant.context;

import java.util.UUID;

public class TenantContext {
    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    public static UUID getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void setCurrentTenant(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
