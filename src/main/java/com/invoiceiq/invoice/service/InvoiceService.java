package com.invoiceiq.invoice.service;

import com.invoiceiq.common.exception.ResourceNotFoundException;
import com.invoiceiq.invoice.dto.InvoiceLineItemRequest;
import com.invoiceiq.invoice.dto.InvoiceLineItemResponse;
import com.invoiceiq.invoice.dto.CreateInvoiceRequest;
import com.invoiceiq.invoice.dto.UpdateInvoiceRequest;
import com.invoiceiq.invoice.dto.InvoiceResponse;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceLineItem;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import com.invoiceiq.tenant.context.TenantContext;
import com.invoiceiq.vendor.dto.VendorResponse;
import com.invoiceiq.vendor.entity.Vendor;
import com.invoiceiq.vendor.repository.VendorRepository;
import com.invoiceiq.auth.repository.UserRepository;
import com.invoiceiq.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public record CreateResult(InvoiceResponse response, boolean isNew) {}

    @Transactional
    public CreateResult createInvoice(CreateInvoiceRequest request, String submitterEmail, String idempotencyKey) {
        UUID tenantId = TenantContext.getCurrentTenant();
        String requestHash = String.valueOf(request.hashCode());
        
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = invoiceRepository.findByIdempotencyKeyAndTenantId(idempotencyKey, tenantId);
            if (existing.isPresent()) {
                if (existing.get().getRequestHash() != null && !existing.get().getRequestHash().equals(requestHash)) {
                    throw new org.springframework.dao.DataIntegrityViolationException("Idempotency key already used with different payload");
                }
                return new CreateResult(mapToResponse(existing.get()), false);
            }
        }

        Vendor vendor = vendorRepository.findByIdAndTenantId(request.getVendorId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));

        var submitter = userRepository.findByEmailAndTenantId(submitterEmail, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setVendor(vendor);
        invoice.setSubmitterId(submitter.getId());
        invoice.setInvoiceNumber(request.getInvoiceNumber());
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setCurrency(request.getCurrency());
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setIdempotencyKey(idempotencyKey);
        invoice.setRequestHash(requestHash);
        
        // Calculate totals and set line items
        BigDecimal totalAmount = BigDecimal.ZERO;
        
        for (InvoiceLineItemRequest itemReq : request.getLineItems()) {
            InvoiceLineItem item = new InvoiceLineItem();
            item.setDescription(itemReq.getDescription());
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(itemReq.getUnitPrice());
            
            BigDecimal lineTotal = itemReq.getQuantity().multiply(itemReq.getUnitPrice()).setScale(4, RoundingMode.HALF_UP);
            item.setTotalPrice(lineTotal);
            
            totalAmount = totalAmount.add(lineTotal);
            
            invoice.addLineItem(item);
        }
        
        invoice.setTotalAmount(totalAmount);
        
        Invoice saved = invoiceRepository.save(invoice);
        return new CreateResult(mapToResponse(saved), true);
    }
    
    @Transactional(readOnly = true)
    public Page<InvoiceResponse> getAllInvoices(Pageable pageable) {
        return invoiceRepository.findAllByTenantId(TenantContext.getCurrentTenant(), pageable)
                .map(this::mapToResponse);
    }
    
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID id) {
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        return mapToResponse(invoice);
    }
    
    @Transactional(readOnly = true)
    public java.util.Optional<InvoiceResponse> findByIdempotencyKey(String idempotencyKey) {
        return invoiceRepository.findByIdempotencyKeyAndTenantId(idempotencyKey, TenantContext.getCurrentTenant())
                .map(this::mapToResponse);
    }
    
    @Transactional
    @SuppressWarnings("null")
    public InvoiceResponse updateInvoice(UUID id, UpdateInvoiceRequest request) {
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
                
        if (!invoice.getVersion().equals(request.getVersion())) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(Invoice.class, id);
        }
                
        Vendor vendor = vendorRepository.findByIdAndTenantId(request.getVendorId(), TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor not found"));
                
        invoice.setVendor(vendor);
        invoice.setInvoiceNumber(request.getInvoiceNumber());
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setDueDate(request.getDueDate());
        invoice.setCurrency(request.getCurrency());
        
        // Remove existing items (orphan removal will handle them)
        invoice.getLineItems().clear();
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (InvoiceLineItemRequest itemReq : request.getLineItems()) {
            InvoiceLineItem item = new InvoiceLineItem();
            item.setDescription(itemReq.getDescription());
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPrice(itemReq.getUnitPrice());
            
            BigDecimal lineTotal = itemReq.getQuantity().multiply(itemReq.getUnitPrice()).setScale(4, RoundingMode.HALF_UP);
            item.setTotalPrice(lineTotal);
            
            totalAmount = totalAmount.add(lineTotal);
            
            invoice.addLineItem(item);
        }
        
        invoice.setTotalAmount(totalAmount);
        
        return mapToResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public void deleteInvoice(UUID id) {
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
        invoiceRepository.delete(invoice);
    }
    
    @Transactional
    public InvoiceResponse submitInvoice(UUID id, String submitterEmail) {
        UUID tenantId = TenantContext.getCurrentTenant();
        
        Invoice invoice = invoiceRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found"));
                
        var user = userRepository.findByEmailAndTenantId(submitterEmail, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                
        if (!user.getRole().name().equals("ADMIN") && !invoice.getSubmitterId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("User is not authorized to submit this invoice");
        }
        
        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalArgumentException("Invoice must be in DRAFT status to be submitted");
        }
        
        String oldState = invoice.getStatus().name();
        invoice.setStatus(InvoiceStatus.NEEDS_REVIEW);
        Invoice saved = invoiceRepository.save(invoice);
        
        auditLogService.recordEvent("INVOICE", invoice.getId(), "SUBMITTED", oldState, "NEEDS_REVIEW", "Invoice submitted for review", user.getId());
        
        return mapToResponse(saved);
    }

    private InvoiceResponse mapToResponse(Invoice invoice) {
        InvoiceResponse res = new InvoiceResponse();
        res.setId(invoice.getId());
        res.setInvoiceNumber(invoice.getInvoiceNumber());
        res.setSubmitterId(invoice.getSubmitterId());
        res.setStatus(invoice.getStatus());
        res.setTotalAmount(invoice.getTotalAmount());
        res.setTaxAmount(invoice.getTaxAmount());
        res.setCurrency(invoice.getCurrency());
        res.setInvoiceDate(invoice.getInvoiceDate());
        res.setDueDate(invoice.getDueDate());
        res.setS3Key(invoice.getS3Key());
        res.setVersion(invoice.getVersion());
        res.setCreatedAt(invoice.getCreatedAt());
        res.setUpdatedAt(invoice.getUpdatedAt());
        
        if (invoice.getVendor() != null) {
            VendorResponse vr = new VendorResponse();
            vr.setId(invoice.getVendor().getId());
            vr.setName(invoice.getVendor().getName());
            vr.setTaxId(invoice.getVendor().getTaxId());
            vr.setAddress(invoice.getVendor().getAddress());
            vr.setBankDetails(invoice.getVendor().getBankDetails());
            res.setVendor(vr);
        }
        
        if (invoice.getLineItems() != null) {
            List<InvoiceLineItemResponse> lines = invoice.getLineItems().stream().map(item -> {
                InvoiceLineItemResponse lr = new InvoiceLineItemResponse();
                lr.setId(item.getId());
                lr.setDescription(item.getDescription());
                lr.setQuantity(item.getQuantity());
                lr.setUnitPrice(item.getUnitPrice());
                lr.setTotalPrice(item.getTotalPrice());
                return lr;
            }).collect(Collectors.toList());
            res.setLineItems(lines);
        }
        
        return res;
    }
}
