package com.invoiceiq.invoice.controller;

import com.invoiceiq.invoice.dto.CreateInvoiceRequest;
import com.invoiceiq.invoice.dto.UpdateInvoiceRequest;
import com.invoiceiq.invoice.dto.InvoiceResponse;
import com.invoiceiq.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;
    private static final List<String> ALLOWED_SORT_FIELDS = Arrays.asList("invoiceNumber", "invoiceDate", "dueDate", "totalAmount", "createdAt", "updatedAt");

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER', 'SUBMITTER')")
    public ResponseEntity<Page<InvoiceResponse>> getAllInvoices(Pageable pageable) {
        int pageSize = pageable.getPageSize();
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }
        
        Sort validSort = Sort.unsorted();
        if (pageable.getSort().isSorted()) {
            for (Sort.Order order : pageable.getSort()) {
                if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                    throw new IllegalArgumentException("Invalid sort field"); 
                }
            }
            validSort = pageable.getSort();
        }
        
        Pageable validatedPageable = PageRequest.of(pageable.getPageNumber(), pageSize, validSort);
        return ResponseEntity.ok(invoiceService.getAllInvoices(validatedPageable));
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER', 'SUBMITTER')")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.getInvoice(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUBMITTER')")
    @SuppressWarnings("null")
    public ResponseEntity<InvoiceResponse> createInvoice(
            @Valid @RequestBody CreateInvoiceRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
            
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        
        var result = invoiceService.createInvoice(request, email, idempotencyKey);
        
        if (result.isNew()) {
            return ResponseEntity.created(URI.create("/api/invoices/" + result.response().getId())).body(result.response());
        } else {
            return ResponseEntity.ok(result.response());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    public ResponseEntity<InvoiceResponse> updateInvoice(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateInvoiceRequest request) {
        return ResponseEntity.ok(invoiceService.updateInvoice(id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUBMITTER')")
    public ResponseEntity<InvoiceResponse> submitInvoice(@PathVariable UUID id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        return ResponseEntity.ok(invoiceService.submitInvoice(id, email));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteInvoice(@PathVariable UUID id) {
        invoiceService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }
}
