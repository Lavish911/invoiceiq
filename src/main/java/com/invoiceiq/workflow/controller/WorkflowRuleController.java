package com.invoiceiq.workflow.controller;

import com.invoiceiq.workflow.dto.WorkflowRuleRequest;
import com.invoiceiq.workflow.dto.WorkflowRuleResponse;
import com.invoiceiq.workflow.entity.WorkflowRule;
import com.invoiceiq.workflow.service.WorkflowRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/workflow-rules")
@RequiredArgsConstructor
public class WorkflowRuleController {

    private final WorkflowRuleService workflowRuleService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkflowRuleResponse> createRule(@Valid @RequestBody WorkflowRuleRequest request) {
        WorkflowRule rule = workflowRuleService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(rule));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WorkflowRuleResponse>> getRules() {
        List<WorkflowRule> rules = workflowRuleService.getRules();
        List<WorkflowRuleResponse> responses = rules.stream().map(this::mapToResponse).collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkflowRuleResponse> getRule(@PathVariable UUID id) {
        WorkflowRule rule = workflowRuleService.getRule(id);
        return ResponseEntity.ok(mapToResponse(rule));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkflowRuleResponse> updateRule(@PathVariable UUID id, @Valid @RequestBody WorkflowRuleRequest request) {
        WorkflowRule rule = workflowRuleService.updateRule(id, request);
        return ResponseEntity.ok(mapToResponse(rule));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        workflowRuleService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }

    private WorkflowRuleResponse mapToResponse(WorkflowRule rule) {
        WorkflowRuleResponse response = new WorkflowRuleResponse();
        response.setId(rule.getId());
        response.setTenantId(rule.getTenantId());
        response.setName(rule.getName());
        response.setActive(rule.isActive());
        response.setPriority(rule.getPriority());
        response.setMinimumAmount(rule.getMinimumAmount());
        response.setMaximumAmount(rule.getMaximumAmount());
        response.setCurrency(rule.getCurrency());
        response.setRequiredApprovals(rule.getRequiredApprovals());
        response.setCreatedAt(rule.getCreatedAt());
        response.setUpdatedAt(rule.getUpdatedAt());
        response.setVersion(rule.getVersion());
        return response;
    }
}
