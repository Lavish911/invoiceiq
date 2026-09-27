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

@Entity
@Table(name = "invoice")
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
}
