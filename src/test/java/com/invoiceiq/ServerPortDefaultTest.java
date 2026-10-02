package com.invoiceiq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the Railway PORT binding default (M7.2): without PORT in the
 * environment, {@code server.port} resolves to 8080, preserving local
 * and Docker behavior.
 */
@SpringBootTest
@ActiveProfiles("test")
class ServerPortDefaultTest {

    @Autowired
    private Environment environment;

    @Test
    void serverPortDefaultsTo8080WithoutPortEnv() {
        assertEquals("8080", environment.getProperty("server.port"),
                "server.port must default to 8080 when PORT is absent");
    }
}
