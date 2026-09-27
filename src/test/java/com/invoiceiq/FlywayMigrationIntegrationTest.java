package com.invoiceiq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import jakarta.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("testcontainers")
@Testcontainers(disabledWithoutDocker = true)
public class FlywayMigrationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private EntityManager entityManager;

    @Test
    void testFlywayMigrationAndJpaValidation() {
        // If the application context starts up, Flyway migrations ran successfully
        // and JPA ddl-auto=validate passed against the created schema.
        assertThat(entityManager).isNotNull();
    }
}
