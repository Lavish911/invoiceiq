package com.invoiceiq.vendor.entity;

import com.invoiceiq.common.entity.BaseTenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "vendor", uniqueConstraints = @UniqueConstraint(name = "uk_vendor_tenant_name", columnNames = {
        "tenant_id", "name" }))
@Getter
@Setter
public class Vendor extends BaseTenantEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Column(name = "bank_details", columnDefinition = "TEXT")
    private String bankDetails;
}
