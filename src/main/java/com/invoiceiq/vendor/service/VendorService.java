package com.invoiceiq.vendor.service;

import com.invoiceiq.vendor.entity.Vendor;
import com.invoiceiq.vendor.repository.VendorRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Vendor helpers. The "Unassigned Vendor" placeholder lets invoice flows that
 * start from a bare document exist without making {@code vendor_id} nullable
 * (which would void duplicate protection, since PostgreSQL treats NULLs as
 * distinct). The V8 {@code uk_vendor_tenant_name} index converts a concurrent
 * double-create into a catchable violation so get-or-create stays single-row.
 */
@Service
@RequiredArgsConstructor
public class VendorService {

    public static final String UNASSIGNED_VENDOR_NAME = "Unassigned Vendor";

    private final VendorRepository vendorRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    @Lazy
    private VendorService self;

    @Transactional
    public Vendor getOrCreatePlaceholder(UUID tenantId) {
        // Bounded retries: losers may re-read before the winner commits, and
        // databases surface contention differently (unique violation on
        // PostgreSQL, lock timeout on H2). Catching broadly here is safe: the
        // loop only retries creation of this single deterministic row, gives
        // up after 5 attempts, and rethrows the last error unchanged — a
        // genuine failure still fails loudly instead of being masked.
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return self.createOnce(tenantId);
            } catch (RuntimeException race) {
                lastFailure = race;
            }
        }
        throw lastFailure;
    }

    /**
     * Isolated transaction so a lost creation race rolls back only this
     * attempt: retrying in the caller's transaction would inherit a
     * rollback-only state and fail unconditionally.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Vendor createOnce(UUID tenantId) {
        return vendorRepository.findByTenantIdAndName(tenantId, UNASSIGNED_VENDOR_NAME)
                .orElseGet(() -> createPlaceholder(tenantId));
    }

    private Vendor createPlaceholder(UUID tenantId) {
        Vendor vendor = new Vendor();
        vendor.setTenantId(tenantId);
        vendor.setName(UNASSIGNED_VENDOR_NAME);
        try {
            return vendorRepository.saveAndFlush(vendor);
        } catch (DataIntegrityViolationException race) {
            // Another thread won the race. The failed insert must be evicted
            // or Hibernate replays it on the next flush (same session state
            // bug as quota creation) before re-reading the winner's row.
            entityManager.clear();
            return vendorRepository.findByTenantIdAndName(tenantId, UNASSIGNED_VENDOR_NAME)
                    .orElseThrow(() -> race);
        }
    }

    @Transactional(readOnly = true)
    public List<Vendor> listByTenant(UUID tenantId) {
        return vendorRepository.findByTenantIdOrderByName(tenantId);
    }
}
