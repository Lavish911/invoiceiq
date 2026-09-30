package com.invoiceiq.invoice.dto;

import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.vendor.dto.VendorResponse;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class InvoiceResponse {
    private UUID id;
    private String invoiceNumber;
    private VendorResponse vendor;
    private UUID submitterId;
    private InvoiceStatus status;
    private BigDecimal totalAmount;
    private BigDecimal taxAmount;
    private String currency;
    private LocalDate invoiceDate;
    private LocalDate dueDate;
    private String s3Key;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<InvoiceLineItemResponse> lineItems;
}
