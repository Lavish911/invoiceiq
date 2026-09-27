package com.invoiceiq.invoice.controller;

import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import com.invoiceiq.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER', 'SUBMITTER')")
    public ResponseEntity<List<Invoice>> getAllInvoices() {
        return ResponseEntity.ok(invoiceRepository.findAllByTenantId(TenantContext.getCurrentTenant()));
    }

    @org.springframework.web.bind.annotation.PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUBMITTER')")
    public ResponseEntity<Invoice> createInvoice() {
        // dummy implementation
        return ResponseEntity.ok(new Invoice());
    }

    @org.springframework.web.bind.annotation.PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    public ResponseEntity<Invoice> approveInvoice(@org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        // dummy implementation
        return ResponseEntity.ok(new Invoice());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteInvoice(@org.springframework.web.bind.annotation.PathVariable java.util.UUID id) {
        invoiceRepository.deleteByIdAndTenantId(id, TenantContext.getCurrentTenant());
        return ResponseEntity.ok().build();
    }
}
