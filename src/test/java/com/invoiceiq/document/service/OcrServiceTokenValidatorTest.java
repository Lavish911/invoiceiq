package com.invoiceiq.document.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves the docker-profile AI_SERVICE_TOKEN fail-fast guard (M7.2):
 * missing, blank, and publicly known development tokens must abort startup,
 * while an explicit token passes. Plain unit test — no Spring context needed.
 */
class OcrServiceTokenValidatorTest {

    @Test
    void rejectsKnownDevelopmentDefault() {
        OcrServiceTokenValidator validator =
                new OcrServiceTokenValidator(OcrServiceTokenValidator.DEV_DEFAULT_TOKEN);
        assertThrows(IllegalStateException.class, validator::validateTokenConfigured,
                "default_dev_token must never boot a docker-profile runtime");
    }

    @Test
    void rejectsDocumentationExamplePlaceholder() {
        OcrServiceTokenValidator validator =
                new OcrServiceTokenValidator(OcrServiceTokenValidator.DOC_EXAMPLE_TOKEN);
        assertThrows(IllegalStateException.class, validator::validateTokenConfigured,
                "the publicly known .env.example placeholder must never boot a docker-profile runtime");
    }

    @Test
    void rejectsMissingToken() {
        assertThrows(IllegalStateException.class,
                () -> new OcrServiceTokenValidator("").validateTokenConfigured(),
                "empty AI_SERVICE_TOKEN must fail fast");
        assertThrows(IllegalStateException.class,
                () -> new OcrServiceTokenValidator("   ").validateTokenConfigured(),
                "blank AI_SERVICE_TOKEN must fail fast");
    }

    @Test
    void acceptsExplicitToken() {
        OcrServiceTokenValidator validator =
                new OcrServiceTokenValidator("s3cr3t-rotated-for-production-9f2c");
        assertDoesNotThrow(validator::validateTokenConfigured);
    }
}
