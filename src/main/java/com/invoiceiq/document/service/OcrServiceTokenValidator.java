package com.invoiceiq.document.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Fail-fast guard for the shared backend-to-OCR credential.
 *
 * <p>Active only under the {@code docker} profile (production-like runtimes such as
 * Docker Compose and Railway). The local/test profiles intentionally keep the
 * development default in {@code PythonExtractionClient} so plain local runs stay
 * convenient. Follows the {@code JwtService} fail-fast pattern: a missing, blank, or
 * publicly known development token must abort startup instead of silently boot
 * a production runtime on a compromised credential.
 */
@Service
@Profile("docker")
public class OcrServiceTokenValidator {

    static final String DEV_DEFAULT_TOKEN = "default_dev_token";
    static final String DOC_EXAMPLE_TOKEN = "dev-local-token-change-me";

    private final String aiServiceToken;

    public OcrServiceTokenValidator(@Value("${AI_SERVICE_TOKEN:}") String aiServiceToken) {
        this.aiServiceToken = aiServiceToken;
    }

    @PostConstruct
    void validateTokenConfigured() {
        if (aiServiceToken == null
                || aiServiceToken.isBlank()
                || DEV_DEFAULT_TOKEN.equals(aiServiceToken)
                || DOC_EXAMPLE_TOKEN.equals(aiServiceToken)) {
            throw new IllegalStateException(
                    "AI_SERVICE_TOKEN is not configured for the docker profile. "
                            + "Set a strong random AI_SERVICE_TOKEN environment variable; "
                            + "missing, blank, and publicly known development defaults are refused.");
        }
    }
}
