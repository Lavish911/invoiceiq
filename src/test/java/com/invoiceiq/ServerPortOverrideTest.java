package com.invoiceiq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the Railway PORT binding (M7.2): when PORT is supplied, Spring Boot
 * picks it up as {@code server.port} via the {@code ${PORT:8080}} placeholder.
 */
@SpringBootTest(properties = "PORT=9199")
@ActiveProfiles("test")
class ServerPortOverrideTest {

    @Autowired
    private Environment environment;

    @Test
    void serverPortFollowsPortProperty() {
        assertEquals("9199", environment.getProperty("server.port"),
                "server.port must follow PORT so Railway can inject its listen port");
    }
}
