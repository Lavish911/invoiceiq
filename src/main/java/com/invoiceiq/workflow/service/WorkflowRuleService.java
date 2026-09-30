package com.invoiceiq.workflow.service;

import com.invoiceiq.tenant.context.TenantContext;
import com.invoiceiq.workflow.dto.WorkflowRuleRequest;
import com.invoiceiq.workflow.entity.WorkflowRule;
import com.invoiceiq.workflow.repository.WorkflowRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkflowRuleService {

    private final WorkflowRuleRepository workflowRuleRepository;

    @Transactional
    public WorkflowRule createRule(WorkflowRuleRequest request) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context found");
        }
        
        WorkflowRule rule = new WorkflowRule();
        rule.setTenantId(tenantId);
        rule.setName(request.getName());
        rule.setActive(request.isActive());
        rule.setPriority(request.getPriority());
        rule.setMinimumAmount(request.getMinimumAmount());
        rule.setMaximumAmount(request.getMaximumAmount());
        rule.setCurrency(request.getCurrency());
        rule.setRequiredApprovals(request.getRequiredApprovals());

        return workflowRuleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public List<WorkflowRule> getRules() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context found");
        }
        return workflowRuleRepository.findByTenantIdOrderByPriorityAsc(tenantId);
    }

    @Transactional(readOnly = true)
    public WorkflowRule getRule(UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new IllegalStateException("No tenant context found");
        }
        return workflowRuleRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow rule not found"));
    }

    @Transactional
    public WorkflowRule updateRule(UUID id, WorkflowRuleRequest request) {
        WorkflowRule rule = getRule(id);
        
        rule.setName(request.getName());
        rule.setActive(request.isActive());
        rule.setPriority(request.getPriority());
        rule.setMinimumAmount(request.getMinimumAmount());
        rule.setMaximumAmount(request.getMaximumAmount());
        rule.setCurrency(request.getCurrency());
        rule.setRequiredApprovals(request.getRequiredApprovals());
        
        return workflowRuleRepository.save(rule);
    }

    @Transactional
    public void deleteRule(UUID id) {
        WorkflowRule rule = getRule(id);
        workflowRuleRepository.delete(rule);
    }
}
