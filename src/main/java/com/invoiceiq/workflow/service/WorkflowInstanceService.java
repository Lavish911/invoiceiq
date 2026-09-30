package com.invoiceiq.workflow.service;

import com.invoiceiq.workflow.exception.WorkflowConflictException;

import com.invoiceiq.audit.service.AuditLogService;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.UserRepository;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import com.invoiceiq.notification.entity.NotificationChannel;
import com.invoiceiq.notification.service.NotificationService;
import com.invoiceiq.tenant.context.TenantContext;
import com.invoiceiq.workflow.dto.ApprovalRequest;
import com.invoiceiq.workflow.entity.ApprovalStep;
import com.invoiceiq.workflow.entity.ApprovalStepStatus;
import com.invoiceiq.workflow.entity.WorkflowInstance;
import com.invoiceiq.workflow.entity.WorkflowRule;
import com.invoiceiq.workflow.entity.WorkflowStatus;
import com.invoiceiq.workflow.repository.ApprovalStepRepository;
import com.invoiceiq.workflow.repository.WorkflowInstanceRepository;
import com.invoiceiq.workflow.repository.WorkflowRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowInstanceService {

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final WorkflowRuleRepository workflowRuleRepository;
    private final InvoiceRepository invoiceRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @Transactional
    public void initiateWorkflow(UUID invoiceId, UUID userId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context found");
        }

        Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found"));

        if (invoice.getStatus() != InvoiceStatus.NEEDS_REVIEW) {
            throw new IllegalStateException("Invoice must be in NEEDS_REVIEW status to initiate workflow");
        }

        // Find applicable rule (simple implementation: match first active rule by currency and amount)
        List<WorkflowRule> rules = workflowRuleRepository.findByTenantIdOrderByPriorityAsc(tenantId);
        WorkflowRule matchedRule = null;
        for (WorkflowRule rule : rules) {
            if (!rule.isActive()) continue;
            boolean match = true;
            if (rule.getCurrency() != null && !rule.getCurrency().equals(invoice.getCurrency())) {
                match = false;
            }
            if (rule.getMinimumAmount() != null && invoice.getTotalAmount().compareTo(rule.getMinimumAmount()) < 0) {
                match = false;
            }
            if (rule.getMaximumAmount() != null && invoice.getTotalAmount().compareTo(rule.getMaximumAmount()) >= 0) {
                match = false;
            }
            if (match) {
                matchedRule = rule;
                break;
            }
        }

        if (matchedRule == null || matchedRule.getRequiredApprovals() == 0) {
            // Auto approve
            String oldState = invoice.getStatus().name();
            invoice.setStatus(InvoiceStatus.APPROVED);
            invoiceRepository.save(invoice);
            
            auditLogService.recordEvent("INVOICE", invoiceId, "AUTO_APPROVE", oldState, "APPROVED", "No approval rules matched or 0 approvals required", userId);
            
            notificationService.createNotification(invoice, null, "SUBMITTER", NotificationChannel.IN_APP, "Invoice Auto-Approved", "Invoice " + invoice.getInvoiceNumber() + " was automatically approved.", tenantId);
            return;
        }

        // Create Workflow
        WorkflowInstance instance = new WorkflowInstance();
        instance.setTenantId(tenantId);
        instance.setInvoice(invoice);
        instance.setStatus(WorkflowStatus.PENDING_APPROVAL);
        instance.setCurrentStep(1);

        // Create Approval Steps
        for (int i = 1; i <= matchedRule.getRequiredApprovals(); i++) {
            ApprovalStep step = new ApprovalStep();
            step.setTenantId(tenantId);
            step.setWorkflowInstance(instance);
            step.setStepNumber(i);
            step.setApproverRole("APPROVER");
            step.setStatus(ApprovalStepStatus.PENDING);
            instance.getApprovalSteps().add(step);
        }

        instance = workflowInstanceRepository.save(instance);

        String oldState = invoice.getStatus().name();
        invoice.setStatus(InvoiceStatus.PENDING_APPROVAL);
        invoiceRepository.save(invoice);

        auditLogService.recordEvent("INVOICE", invoiceId, "WORKFLOW_CREATED", oldState, "PENDING_APPROVAL", "Workflow created. Rule matched: " + matchedRule.getName(), userId);
        
        notificationService.createNotification(invoice, null, "APPROVER", NotificationChannel.IN_APP, "Approval Required", "Invoice " + invoice.getInvoiceNumber() + " requires approval.", tenantId);
    }

    @Transactional
    public void approveStep(UUID invoiceId, UUID userId, ApprovalRequest request) {
        processAction(invoiceId, userId, request, true);
    }

    @Transactional
    public void rejectStep(UUID invoiceId, UUID userId, ApprovalRequest request) {
        processAction(invoiceId, userId, request, false);
    }

    private void processAction(UUID invoiceId, UUID userId, ApprovalRequest request, boolean isApprove) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context found");
        }

        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!user.getRole().name().equals("APPROVER") && !user.getRole().name().equals("ADMIN")) {
            throw new IllegalStateException("User does not have permission to approve/reject");
        }

        WorkflowInstance instance = workflowInstanceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));

        if (instance.getStatus() != WorkflowStatus.PENDING_APPROVAL) {
            throw new WorkflowConflictException("Workflow is not pending approval");
        }
        
        if (!instance.getVersion().equals(request.getExpectedVersion())) {
             throw new WorkflowConflictException("Concurrency conflict: Workflow instance has been modified by another transaction.");
        }

        ApprovalStep currentStep = approvalStepRepository.findByWorkflowInstanceIdAndStepNumberAndTenantId(instance.getId(), instance.getCurrentStep(), tenantId)
                .orElseThrow(() -> new IllegalStateException("Current step not found"));

        if (currentStep.getStatus() != ApprovalStepStatus.PENDING) {
            throw new WorkflowConflictException("Current step is already completed");
        }

        currentStep.setStatus(isApprove ? ApprovalStepStatus.APPROVED : ApprovalStepStatus.REJECTED);
        currentStep.setActedBy(user);
        currentStep.setActedAt(java.time.OffsetDateTime.now());
        currentStep.setComment(request.getComment());
        approvalStepRepository.save(currentStep);

        Invoice invoice = instance.getInvoice();
        String oldState = invoice.getStatus().name();

        if (!isApprove) {
            instance.setStatus(WorkflowStatus.REJECTED);
            workflowInstanceRepository.save(instance);

            invoice.setStatus(InvoiceStatus.REJECTED);
            invoiceRepository.save(invoice);
            
            auditLogService.recordEvent("INVOICE", invoiceId, "REJECTED", oldState, "REJECTED", request.getComment(), userId);
            
            User submitter = userRepository.findByIdAndTenantId(invoice.getSubmitterId(), tenantId).orElse(null);
            notificationService.createNotification(invoice, submitter, "SUBMITTER", NotificationChannel.IN_APP, "Invoice Rejected", "Invoice " + invoice.getInvoiceNumber() + " was rejected.", tenantId);
        } else {
            if (instance.getCurrentStep() >= instance.getApprovalSteps().size()) {
                instance.setStatus(WorkflowStatus.APPROVED);
                workflowInstanceRepository.save(instance);

                invoice.setStatus(InvoiceStatus.APPROVED);
                invoiceRepository.save(invoice);
                
                auditLogService.recordEvent("INVOICE", invoiceId, "APPROVED", oldState, "APPROVED", request.getComment(), userId);
                
                User submitter = userRepository.findByIdAndTenantId(invoice.getSubmitterId(), tenantId).orElse(null);
                notificationService.createNotification(invoice, submitter, "SUBMITTER", NotificationChannel.IN_APP, "Invoice Approved", "Invoice " + invoice.getInvoiceNumber() + " was fully approved.", tenantId);
            } else {
                instance.setCurrentStep(instance.getCurrentStep() + 1);
                workflowInstanceRepository.save(instance);
                auditLogService.recordEvent("WORKFLOW", instance.getId(), "STEP_APPROVED", null, null, request.getComment(), userId);
            }
        }
    }
    
    @Transactional(readOnly = true)
    public WorkflowInstance getWorkflowForInvoice(UUID invoiceId) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context found");
        }
        return workflowInstanceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
    }
}
