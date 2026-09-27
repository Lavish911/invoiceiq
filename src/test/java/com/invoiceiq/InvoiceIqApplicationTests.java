package com.invoiceiq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class InvoiceIqApplicationTests {

    @Test
    void contextLoads() {
        // Test that spring context starts and flyway migrations run successfully
    }
}
