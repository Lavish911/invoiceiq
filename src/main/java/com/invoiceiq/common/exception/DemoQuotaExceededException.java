package com.invoiceiq.common.exception;

/**
 * Thrown when the demo tenant exceeds its upload quota. Mapped to HTTP 429.
 */
public class DemoQuotaExceededException extends RuntimeException {

    public DemoQuotaExceededException(String message) {
        super(message);
    }
}
