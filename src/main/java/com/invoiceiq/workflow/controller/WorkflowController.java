package com.invoiceiq.workflow.controller;

import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.UserRepository;
import com.invoiceiq.tenant.context.TenantContext;
import com.invoiceiq.workflow.dto.ApprovalRequest;
import com.invoiceiq.workflow.dto.ApprovalStepResponse;
import com.invoiceiq.workflow.dto.WorkflowInstanceResponse;
import com.invoiceiq.workflow.entity.WorkflowInstance;
import com.invoiceiq.workflow.service.WorkflowInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/invoices/{invoiceId}")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowInstanceService workflowInstanceService;
    private final UserRepository userRepository;

    @PostMapping("/workflow/initiate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUBMITTER')")
    public ResponseEntity<Void> initiateWorkflow(@PathVariable UUID invoiceId) {
        workflowInstanceService.initiateWorkflow(invoiceId, getCurrentUserId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/workflow")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER', 'SUBMITTER')")
    public ResponseEntity<WorkflowInstanceResponse> getWorkflow(@PathVariable UUID invoiceId) {
        WorkflowInstance instance = workflowInstanceService.getWorkflowForInvoice(invoiceId);
        return ResponseEntity.ok(mapToResponse(instance));
    }

    @PostMapping("/approval/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    public ResponseEntity<Void> approve(@PathVariable UUID invoiceId, @Valid @RequestBody ApprovalRequest request) {
        workflowInstanceService.approveStep(invoiceId, getCurrentUserId(), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/approval/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'APPROVER')")
    public ResponseEntity<Void> reject(@PathVariable UUID invoiceId, @Valid @RequestBody ApprovalRequest request) {
        workflowInstanceService.rejectStep(invoiceId, getCurrentUserId(), request);
        return ResponseEntity.ok().build();
    }

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth.getName();
        UUID tenantId = TenantContext.getCurrentTenant();
        User user = userRepository.findByEmailAndTenantId(email, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return user.getId();
    }

    private WorkflowInstanceResponse mapToResponse(WorkflowInstance instance) {
        WorkflowInstanceResponse response = new WorkflowInstanceResponse();
        response.setId(instance.getId());
        response.setTenantId(instance.getTenantId());
        response.setInvoiceId(instance.getInvoice().getId());
        response.setStatus(instance.getStatus());
        response.setCurrentStep(instance.getCurrentStep());
        response.setVersion(instance.getVersion());
        response.setCreatedAt(instance.getCreatedAt());
        response.setUpdatedAt(instance.getUpdatedAt());
        
        response.setApprovalSteps(instance.getApprovalSteps().stream().map(step -> {
            ApprovalStepResponse sr = new ApprovalStepResponse();
            sr.setId(step.getId());
            sr.setStepNumber(step.getStepNumber());
            sr.setApproverRole(step.getApproverRole());
            sr.setStatus(step.getStatus());
            if (step.getActedBy() != null) sr.setActedBy(step.getActedBy().getId());
            sr.setActedAt(step.getActedAt());
            sr.setComment(step.getComment());
            return sr;
        }).collect(Collectors.toList()));
        
        return response;
    }
}
