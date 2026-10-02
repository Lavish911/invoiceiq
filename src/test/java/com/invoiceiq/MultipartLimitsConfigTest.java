package com.invoiceiq;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.unit.DataSize;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the M7.2 multipart hardening: the local and docker profiles declare
 * explicit 10 MB framework limits that bind to Spring Boot's
 * {@code MultipartProperties}, matching the application-level 10 MB upload
 * contract enforced in {@code DocumentService}.
 */
class MultipartLimitsConfigTest {

    @ParameterizedTest(name = "{0} declares bindable 10MB multipart limits")
    @ValueSource(strings = {"application-local.yml", "application-docker.yml"})
    void multipartLimitsArePresentAndBindable(String resource) throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        List<PropertySource<?>> sources = loader.load(resource, new ClassPathResource(resource));
        assertTrue(sources != null && !sources.isEmpty(), resource + " must parse as YAML");

        MultipartProperties bound = new Binder(ConfigurationPropertySources.from(sources))
                .bind("spring.servlet.multipart", MultipartProperties.class)
                .orElseThrow(() -> new AssertionError(
                        resource + " must bind spring.servlet.multipart"));

        assertEquals(DataSize.ofMegabytes(10), bound.getMaxFileSize(),
                resource + ": max-file-size must be 10MB");
        assertEquals(DataSize.ofMegabytes(10), bound.getMaxRequestSize(),
                resource + ": max-request-size must be 10MB");
    }
}
