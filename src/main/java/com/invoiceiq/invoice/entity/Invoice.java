package com.invoiceiq.invoice.entity;

import com.invoiceiq.common.entity.BaseTenantEntity; // Trigger IDE sync
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.invoiceiq.vendor.entity.Vendor;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;

@Entity
@Table(name = "invoice", uniqueConstraints = {
    @jakarta.persistence.UniqueConstraint(name = "uk_invoice_tenant_vendor_number", columnNames = {"tenant_id", "vendor_id", "invoice_number"}),
    @jakarta.persistence.UniqueConstraint(name = "uk_invoice_tenant_idempotency", columnNames = {"tenant_id", "idempotency_key"})
})
@Getter
@Setter
public class Invoice extends BaseTenantEntity {

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Column(name = "submitter_id", nullable = false)
    private UUID submitterId;

    @Column(name = "tax_amount", precision = 19, scale = 4)
    private BigDecimal taxAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "s3_key", length = 1024)
    private String s3Key;

    @Column(name = "idempotency_key")
    private String idempotencyKey;
    
    @Column(name = "request_hash")
    private String requestHash;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    public void addLineItem(InvoiceLineItem item) {
        lineItems.add(item);
        item.setInvoice(this);
    }

    public void removeLineItem(InvoiceLineItem item) {
        lineItems.remove(item);
        item.setInvoice(null);
    }
}
