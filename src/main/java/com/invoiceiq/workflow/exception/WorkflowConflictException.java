package com.invoiceiq.workflow.exception;

public class WorkflowConflictException extends RuntimeException {
    public WorkflowConflictException(String message) {
        super(message);
    }
}
