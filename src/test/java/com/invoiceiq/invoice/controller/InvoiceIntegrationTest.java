package com.invoiceiq.invoice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.auth.dto.AuthResponse;
import com.invoiceiq.auth.dto.LoginRequest;
import com.invoiceiq.auth.entity.Role;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.UserRepository;
import com.invoiceiq.invoice.dto.InvoiceLineItemRequest;
import com.invoiceiq.invoice.dto.CreateInvoiceRequest;
import com.invoiceiq.invoice.dto.UpdateInvoiceRequest;
import com.invoiceiq.invoice.dto.InvoiceResponse;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import com.invoiceiq.tenant.entity.Tenant;
import com.invoiceiq.tenant.repository.TenantRepository;
import com.invoiceiq.vendor.entity.Vendor;
import com.invoiceiq.vendor.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Docker is a REQUIRED test prerequisite: without it this class must FAIL LOUDLY,
// never silently skip (a skipped run must not be mistaken for full verification).
@Testcontainers
@SuppressWarnings("null")
class InvoiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private VendorRepository vendorRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private ObjectMapper objectMapper;

    private User adminA;
    private String tokenAdminA;
    private User submitterA;
    private String tokenSubmitterA;
    private User approverA;
    private String tokenApproverA;
    private Tenant tenantA;
    private Vendor vendorA;

    private Tenant tenantB;
    private User adminB;
    private String tokenAdminB;
    private Vendor vendorB;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.execute("DELETE FROM extraction_result");
        jdbcTemplate.execute("DELETE FROM document");
        invoiceRepository.deleteAll();
        vendorRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        tenantA = new Tenant();
        tenantA.setId(UUID.randomUUID());
        tenantA.setName("Tenant A");
        tenantA = tenantRepository.save(tenantA);

        tenantB = new Tenant();
        tenantB.setId(UUID.randomUUID());
        tenantB.setName("Tenant B");
        tenantB = tenantRepository.save(tenantB);

        adminA = createUser(tenantA.getId(), "admina@example.com", Role.ADMIN);
        tokenAdminA = getAccessToken("admina@example.com", "password", tenantA.getId());

        submitterA = createUser(tenantA.getId(), "submittera@example.com", Role.SUBMITTER);
        tokenSubmitterA = getAccessToken("submittera@example.com", "password", tenantA.getId());

        approverA = createUser(tenantA.getId(), "approvera@example.com", Role.APPROVER);
        tokenApproverA = getAccessToken("approvera@example.com", "password", tenantA.getId());

        adminB = createUser(tenantB.getId(), "adminb@example.com", Role.ADMIN);
        tokenAdminB = getAccessToken("adminb@example.com", "password", tenantB.getId());

        vendorA = new Vendor();
        vendorA.setId(UUID.randomUUID());
        vendorA.setTenantId(tenantA.getId());
        vendorA.setName("Vendor A");
        vendorA = vendorRepository.save(vendorA);

        vendorB = new Vendor();
        vendorB.setId(UUID.randomUUID());
        vendorB.setTenantId(tenantB.getId());
        vendorB.setName("Vendor B");
        vendorB = vendorRepository.save(vendorB);
    }

    private User createUser(UUID tenantId, String email, Role role) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setTenantId(tenantId);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("password"));
        u.setRole(role);
        return userRepository.save(u);
    }

    private String getAccessToken(String email, String password, UUID tenantId) throws Exception {
        LoginRequest request = new LoginRequest(email, password, tenantId);
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(response, AuthResponse.class).getAccessToken();
    }

    private CreateInvoiceRequest createValidRequest(UUID vId, String num) {
        CreateInvoiceRequest req = new CreateInvoiceRequest();
        req.setVendorId(vId);
        req.setInvoiceNumber(num);
        req.setInvoiceDate(LocalDate.now());
        req.setDueDate(LocalDate.now().plusDays(30));
        req.setCurrency("USD");
        InvoiceLineItemRequest line1 = new InvoiceLineItemRequest();
        line1.setDescription("Service");
        line1.setQuantity(new BigDecimal("1"));
        line1.setUnitPrice(new BigDecimal("100.00"));
        req.setLineItems(List.of(line1));
        return req;
    }
    
    @Test
    void testCreateInvoice_Success() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorA.getId(), "INV-100");
        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vendor.name").value("Vendor A"));
    }

    // 1. ADMIN can successfully delete an invoice.
    @Test
    void adminCanDeleteInvoice() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorA.getId(), "INV-DEL-1");
        String resp = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readValue(resp, InvoiceResponse.class).getId().toString();

        mockMvc.perform(delete("/api/invoices/" + id)
                .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isNoContent());
        
        assertEquals(0, invoiceRepository.count());
    }

    // 2. Non-ADMIN cannot delete an invoice.
    @Test
    void nonAdminCannotDeleteInvoice() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorA.getId(), "INV-DEL-2");
        String resp = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readValue(resp, InvoiceResponse.class).getId().toString();

        mockMvc.perform(delete("/api/invoices/" + id)
                .header("Authorization", "Bearer " + tokenSubmitterA))
                .andExpect(status().isForbidden());
        
        assertEquals(1, invoiceRepository.count());
    }

    // 3. SUBMITTER cannot update an invoice.
    @Test
    void submitterCannotUpdateInvoice() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorA.getId(), "INV-UPD-1");
        String resp = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        InvoiceResponse inv = objectMapper.readValue(resp, InvoiceResponse.class);

        UpdateInvoiceRequest upd = new UpdateInvoiceRequest();
        upd.setVendorId(vendorA.getId());
        upd.setInvoiceNumber("INV-UPD-1X");
        upd.setInvoiceDate(request.getInvoiceDate());
        upd.setDueDate(request.getDueDate());
        upd.setCurrency("USD");
        upd.setLineItems(request.getLineItems());
        upd.setVersion(inv.getVersion());

        mockMvc.perform(put("/api/invoices/" + inv.getId())
                .header("Authorization", "Bearer " + tokenSubmitterA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(upd)))
                .andExpect(status().isForbidden());
    }

    // 4. APPROVER can update an invoice.
    @Test
    void approverCanUpdateInvoice() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorA.getId(), "INV-UPD-2");
        String resp = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
        InvoiceResponse inv = objectMapper.readValue(resp, InvoiceResponse.class);

        UpdateInvoiceRequest upd = new UpdateInvoiceRequest();
        upd.setVendorId(vendorA.getId());
        upd.setInvoiceNumber("INV-UPD-2X");
        upd.setInvoiceDate(request.getInvoiceDate());
        upd.setDueDate(request.getDueDate());
        upd.setCurrency("USD");
        upd.setLineItems(request.getLineItems());
        upd.setVersion(inv.getVersion());

        mockMvc.perform(put("/api/invoices/" + inv.getId())
                .header("Authorization", "Bearer " + tokenApproverA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(upd)))
                .andExpect(status().isOk());
    }

    // 5. Duplicate invoice with same tenant + vendor + invoice number returns 409.
    @Test
    void duplicateInvoiceTenantVendorNumberReturns409() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorA.getId(), "INV-DUP-1");
        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    // 6. Duplicate invoice UPDATE returns 409.
    @Test
    void duplicateInvoiceUpdateReturns409() throws Exception {
        CreateInvoiceRequest request1 = createValidRequest(vendorA.getId(), "INV-DUP-2A");
        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)));

        CreateInvoiceRequest request2 = createValidRequest(vendorA.getId(), "INV-DUP-2B");
        String resp = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andReturn().getResponse().getContentAsString();
        InvoiceResponse inv2 = objectMapper.readValue(resp, InvoiceResponse.class);

        UpdateInvoiceRequest upd = new UpdateInvoiceRequest();
        upd.setVendorId(vendorA.getId());
        upd.setInvoiceNumber("INV-DUP-2A"); 
        upd.setInvoiceDate(request2.getInvoiceDate());
        upd.setDueDate(request2.getDueDate());
        upd.setCurrency("USD");
        upd.setLineItems(request2.getLineItems());
        upd.setVersion(inv2.getVersion());

        mockMvc.perform(put("/api/invoices/" + inv2.getId())
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(upd)))
                .andExpect(status().isConflict());
    }

    // 7. Same idempotency key + same payload returns the existing invoice without creating a duplicate.
    @Test
    void sameIdempotencyKeySamePayloadReturnsExisting() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorA.getId(), "INV-IDEM-1");
        String idemKey = UUID.randomUUID().toString();

        String r1 = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        
        String r2 = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        
        InvoiceResponse inv1 = objectMapper.readValue(r1, InvoiceResponse.class);
        InvoiceResponse inv2 = objectMapper.readValue(r2, InvoiceResponse.class);
        assertEquals(inv1.getId(), inv2.getId());
        assertEquals(1, invoiceRepository.count());
    }

    // 8. Same idempotency key + different payload returns 409.
    @Test
    void sameIdempotencyKeyDifferentPayloadReturns409() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorA.getId(), "INV-IDEM-2A");
        String idemKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        
        CreateInvoiceRequest requestDiff = createValidRequest(vendorA.getId(), "INV-IDEM-2B");
        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDiff)))
                .andExpect(status().isConflict());
    }

    // 9. Same idempotency key in different tenants is independent.
    @Test
    void sameIdempotencyKeyDifferentTenants() throws Exception {
        CreateInvoiceRequest requestA = createValidRequest(vendorA.getId(), "INV-IDEM-T-A");
        CreateInvoiceRequest requestB = createValidRequest(vendorB.getId(), "INV-IDEM-T-B");
        String idemKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .header("X-Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestA)))
                .andExpect(status().isCreated());
        
        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminB)
                .header("X-Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestB)))
                .andExpect(status().isCreated());
        
        assertEquals(2, invoiceRepository.count());
    }

    // 10. Concurrent duplicate invoice creation results in exactly one persisted invoice.
    @Test
    void concurrentDuplicateInvoiceCreation() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorA.getId(), "INV-CONCUR-1");
        String requestJson = objectMapper.writeValueAsString(request);
        
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    int s = mockMvc.perform(post("/api/invoices")
                            .header("Authorization", "Bearer " + tokenAdminA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                            .andReturn().getResponse().getStatus();
                    if (s == 201) successCount.incrementAndGet();
                    if (s == 409) conflictCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }
        
        latch.countDown();
        done.await(10, TimeUnit.SECONDS);
        
        System.out.println("Concurrent duplicate invoice creation invoices persisted: " + invoiceRepository.count());
        assertEquals(1, invoiceRepository.count());
        assertEquals(1, successCount.get());
        assertEquals(threadCount - 1, conflictCount.get());
    }

    // 11. Concurrent same-idempotency-key requests result in exactly one persisted invoice.
    @Test
    void concurrentSameIdempotencyKey() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorA.getId(), "INV-CONCUR-2");
        String requestJson = objectMapper.writeValueAsString(request);
        String idemKey = UUID.randomUUID().toString();
        
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        
        AtomicInteger createdCount = new AtomicInteger(0);
        AtomicInteger okCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    int s = mockMvc.perform(post("/api/invoices")
                            .header("Authorization", "Bearer " + tokenAdminA)
                            .header("X-Idempotency-Key", idemKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                            .andReturn().getResponse().getStatus();
                    if (s == 201) createdCount.incrementAndGet();
                    if (s == 200) okCount.incrementAndGet();
                    if (s == 409) conflictCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }
        
        latch.countDown();
        done.await(10, TimeUnit.SECONDS);
        
        System.out.println("Concurrent same-idempotency-key invoices persisted: " + invoiceRepository.count());
        assertEquals(1, invoiceRepository.count());
        assertEquals(1, createdCount.get());
        assertEquals(threadCount - 1, okCount.get() + conflictCount.get());
    }

    // 12. Page size > 100 is rejected with 400.
    @Test
    void pageSizeGreaterThan100Rejected() throws Exception {
        mockMvc.perform(get("/api/invoices?size=101")
                .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isBadRequest());
    }

    // 13. Invalid sort field is rejected with 400.
    @Test
    void invalidSortFieldRejected() throws Exception {
        mockMvc.perform(get("/api/invoices?sort=passwordHash")
                .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isBadRequest());
    }

    // 14. Tenant A cannot access Tenant B's invoice.
    @Test
    void tenantCannotAccessOtherTenantInvoice() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorB.getId(), "INV-TENANT-B");
        String resp = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        
        String invoiceId = objectMapper.readValue(resp, InvoiceResponse.class).getId().toString();

        mockMvc.perform(get("/api/invoices/" + invoiceId)
                .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isNotFound());
    }

    // 15. Tenant A cannot reference Tenant B's vendor.
    @Test
    void tenantCannotReferenceOtherTenantVendor() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorB.getId(), "INV-VENDOR-STEAL");
        mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound()); 
    }

    // 16. Perform a genuine concurrent optimistic-locking test with two simultaneous updates against the same invoice version.
    @Test
    void genuineConcurrentOptimisticLocking() throws Exception {
        CreateInvoiceRequest request = createValidRequest(vendorA.getId(), "INV-LOCK");
        String resp = mockMvc.perform(post("/api/invoices")
                .header("Authorization", "Bearer " + tokenAdminA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        
        InvoiceResponse inv = objectMapper.readValue(resp, InvoiceResponse.class);

        UpdateInvoiceRequest upd1 = new UpdateInvoiceRequest();
        upd1.setVendorId(vendorA.getId());
        upd1.setInvoiceNumber("INV-LOCK-1");
        upd1.setInvoiceDate(request.getInvoiceDate());
        upd1.setDueDate(request.getDueDate());
        upd1.setCurrency("USD");
        upd1.setLineItems(request.getLineItems());
        upd1.setVersion(inv.getVersion());

        UpdateInvoiceRequest upd2 = new UpdateInvoiceRequest();
        upd2.setVendorId(vendorA.getId());
        upd2.setInvoiceNumber("INV-LOCK-2");
        upd2.setInvoiceDate(request.getInvoiceDate());
        upd2.setDueDate(request.getDueDate());
        upd2.setCurrency("USD");
        upd2.setLineItems(request.getLineItems());
        upd2.setVersion(inv.getVersion());

        String reqJson1 = objectMapper.writeValueAsString(upd1);
        String reqJson2 = objectMapper.writeValueAsString(upd2);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        
        AtomicInteger okCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        executor.submit(() -> {
            try {
                latch.await();
                int s = mockMvc.perform(put("/api/invoices/" + inv.getId())
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson1))
                        .andReturn().getResponse().getStatus();
                if (s == 200) okCount.incrementAndGet();
                if (s == 409) conflictCount.incrementAndGet();
            } catch (Exception e) { e.printStackTrace(); } finally { done.countDown(); }
        });

        executor.submit(() -> {
            try {
                latch.await();
                int s = mockMvc.perform(put("/api/invoices/" + inv.getId())
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson2))
                        .andReturn().getResponse().getStatus();
                if (s == 200) okCount.incrementAndGet();
                if (s == 409) conflictCount.incrementAndGet();
            } catch (Exception e) { e.printStackTrace(); } finally { done.countDown(); }
        });
        
        latch.countDown();
        done.await(10, TimeUnit.SECONDS);

        System.out.println("Concurrent optimistic locking test completed.");
        assertEquals(1, okCount.get());
        assertEquals(1, conflictCount.get());
    }
}
