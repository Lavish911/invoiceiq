package com.invoiceiq.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoiceiq.auth.dto.AuthResponse;
import com.invoiceiq.auth.dto.LoginRequest;
import com.invoiceiq.auth.entity.Role;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.auth.repository.UserRepository;
import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.invoice.entity.InvoiceStatus;
import com.invoiceiq.invoice.repository.InvoiceRepository;
import com.invoiceiq.tenant.entity.Tenant;
import com.invoiceiq.tenant.repository.TenantRepository;
import com.invoiceiq.vendor.entity.Vendor;
import com.invoiceiq.vendor.repository.VendorRepository;
import com.invoiceiq.workflow.dto.ApprovalRequest;
import com.invoiceiq.workflow.entity.WorkflowRule;
import com.invoiceiq.workflow.entity.WorkflowStatus;
import com.invoiceiq.workflow.repository.WorkflowInstanceRepository;
import com.invoiceiq.workflow.repository.WorkflowRuleRepository;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@SuppressWarnings("null")
public class WorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private WorkflowRuleRepository workflowRuleRepository;

    @Autowired
    private WorkflowInstanceRepository workflowInstanceRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private Tenant tenantA;
    private User adminA;
    private String tokenAdminA;
    private User submitterA;
    private String tokenSubmitterA;
    private User approverA;
    private String tokenApproverA;
    private Vendor vendorA;

    @BeforeEach
    void setUp() throws Exception {
        workflowInstanceRepository.deleteAll();
        workflowRuleRepository.deleteAll();
        
        jdbcTemplate.execute("DELETE FROM audit_log");
        jdbcTemplate.execute("DELETE FROM notification");
        jdbcTemplate.execute("DELETE FROM extraction_result");
        jdbcTemplate.execute("DELETE FROM document");
        
        invoiceRepository.deleteAll();
        vendorRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();

        tenantA = new Tenant();
        tenantA.setId(UUID.randomUUID());
        tenantA.setName("Tenant WF");
        tenantA = tenantRepository.save(tenantA);

        adminA = createUser(tenantA.getId(), "admin-wf@example.com", Role.ADMIN);
        tokenAdminA = getAccessToken("admin-wf@example.com", "password", tenantA.getId());

        submitterA = createUser(tenantA.getId(), "submitter-wf@example.com", Role.SUBMITTER);
        tokenSubmitterA = getAccessToken("submitter-wf@example.com", "password", tenantA.getId());

        approverA = createUser(tenantA.getId(), "approver-wf@example.com", Role.APPROVER);
        tokenApproverA = getAccessToken("approver-wf@example.com", "password", tenantA.getId());

        vendorA = new Vendor();
        vendorA.setId(UUID.randomUUID());
        vendorA.setTenantId(tenantA.getId());
        vendorA.setName("Vendor WF");
        vendorA = vendorRepository.save(vendorA);
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

    @Test
    void testWorkflowLifecycle() throws Exception {
        // 1. Create a workflow rule
        WorkflowRule rule = new WorkflowRule();
        rule.setId(UUID.randomUUID());
        rule.setTenantId(tenantA.getId());
        rule.setName("Test Rule");
        rule.setActive(true);
        rule.setPriority(1);
        rule.setRequiredApprovals(1);
        rule.setMinimumAmount(new BigDecimal("100.00"));
        rule = workflowRuleRepository.save(rule);

        // 2. Create Invoice in DRAFT
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(tenantA.getId());
        invoice.setInvoiceNumber("INV-WF-1001");
        invoice.setVendor(vendorA);
        invoice.setTotalAmount(new BigDecimal("500.00"));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setSubmitterId(submitterA.getId());
        invoice = invoiceRepository.save(invoice);

        // 3. Submit invoice (DRAFT -> NEEDS_REVIEW)
        mockMvc.perform(post("/api/invoices/" + invoice.getId() + "/submit")
                .header("Authorization", "Bearer " + tokenSubmitterA))
                .andExpect(status().isOk());

        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updatedInvoice.getStatus()).isEqualTo(InvoiceStatus.NEEDS_REVIEW);

        // 4. Initiate workflow (Submitter)
        mockMvc.perform(post("/api/invoices/" + invoice.getId() + "/workflow/initiate")
                .header("Authorization", "Bearer " + tokenSubmitterA))
                .andExpect(status().isOk());

        // 5. Verify Invoice and Workflow status
        updatedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updatedInvoice.getStatus()).isEqualTo(InvoiceStatus.PENDING_APPROVAL);

        var instance = workflowInstanceRepository.findByInvoiceIdAndTenantId(invoice.getId(), tenantA.getId()).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.PENDING_APPROVAL);

        // 6. Approve step (Approver)
        ApprovalRequest request = new ApprovalRequest();
        request.setExpectedVersion(instance.getVersion());
        request.setComment("Looks good to me");

        mockMvc.perform(post("/api/invoices/" + invoice.getId() + "/approval/approve")
                .header("Authorization", "Bearer " + tokenApproverA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // 7. Verify final state
        updatedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updatedInvoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);

        instance = workflowInstanceRepository.findByInvoiceIdAndTenantId(invoice.getId(), tenantA.getId()).orElseThrow();
        assertThat(instance.getStatus()).isEqualTo(WorkflowStatus.APPROVED);
    }
    
    @Test
    void testSimultaneousApprove() throws Exception {
        WorkflowRule rule = new WorkflowRule();
        rule.setId(UUID.randomUUID());
        rule.setTenantId(tenantA.getId());
        rule.setName("Test Rule");
        rule.setActive(true);
        rule.setPriority(1);
        rule.setRequiredApprovals(1);
        rule.setMinimumAmount(new BigDecimal("100.00"));
        rule = workflowRuleRepository.save(rule);

        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(tenantA.getId());
        invoice.setInvoiceNumber("INV-WF-CONC-1");
        invoice.setVendor(vendorA);
        invoice.setTotalAmount(new BigDecimal("500.00"));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setSubmitterId(submitterA.getId());
        invoice = invoiceRepository.save(invoice);
        final UUID invoiceId = invoice.getId();

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/submit")
                .header("Authorization", "Bearer " + tokenSubmitterA))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/workflow/initiate")
                .header("Authorization", "Bearer " + tokenSubmitterA))
                .andExpect(status().isOk());
                
        var instance = workflowInstanceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantA.getId()).orElseThrow();

        ApprovalRequest request = new ApprovalRequest();
        request.setExpectedVersion(instance.getVersion());
        request.setComment("Concurrent approve");
        
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger status1 = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger status2 = new java.util.concurrent.atomic.AtomicInteger();
        
        Thread t1 = new Thread(() -> {
            try {
                latch.await();
                int st = mockMvc.perform(post("/api/invoices/" + invoiceId + "/approval/approve")
                        .header("Authorization", "Bearer " + tokenApproverA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
                status1.set(st);
            } catch (Exception e) {}
        });
        
        Thread t2 = new Thread(() -> {
            try {
                latch.await();
                int st = mockMvc.perform(post("/api/invoices/" + invoiceId + "/approval/approve")
                        .header("Authorization", "Bearer " + tokenApproverA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
                status2.set(st);
            } catch (Exception e) {}
        });
        
        t1.start();
        t2.start();
        latch.countDown();
        t1.join();
        t2.join();
        
        assertThat(status1.get() == 200 || status2.get() == 200).isTrue();
        assertThat(status1.get() == 409 || status2.get() == 409).isTrue();
        
        Invoice finalInvoice = invoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(finalInvoice.getStatus()).isEqualTo(InvoiceStatus.APPROVED);
    }
    
    @Test
    void testSimultaneousApproveAndReject() throws Exception {
        WorkflowRule rule = new WorkflowRule();
        rule.setId(UUID.randomUUID());
        rule.setTenantId(tenantA.getId());
        rule.setName("Test Rule");
        rule.setActive(true);
        rule.setPriority(1);
        rule.setRequiredApprovals(1);
        rule.setMinimumAmount(new BigDecimal("100.00"));
        rule = workflowRuleRepository.save(rule);

        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(tenantA.getId());
        invoice.setInvoiceNumber("INV-WF-CONC-2");
        invoice.setVendor(vendorA);
        invoice.setTotalAmount(new BigDecimal("500.00"));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setSubmitterId(submitterA.getId());
        invoice = invoiceRepository.save(invoice);
        final UUID invoiceId = invoice.getId();

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/submit")
                .header("Authorization", "Bearer " + tokenSubmitterA))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/invoices/" + invoiceId + "/workflow/initiate")
                .header("Authorization", "Bearer " + tokenSubmitterA))
                .andExpect(status().isOk());
                
        var instance = workflowInstanceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantA.getId()).orElseThrow();

        ApprovalRequest request = new ApprovalRequest();
        request.setExpectedVersion(instance.getVersion());
        request.setComment("Concurrent approve or reject");
        
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger statusApprove = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger statusReject = new java.util.concurrent.atomic.AtomicInteger();
        
        Thread tApprove = new Thread(() -> {
            try {
                latch.await();
                int st = mockMvc.perform(post("/api/invoices/" + invoiceId + "/approval/approve")
                        .header("Authorization", "Bearer " + tokenApproverA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
                statusApprove.set(st);
            } catch (Exception e) {}
        });
        
        Thread tReject = new Thread(() -> {
            try {
                latch.await();
                int st = mockMvc.perform(post("/api/invoices/" + invoiceId + "/approval/reject")
                        .header("Authorization", "Bearer " + tokenApproverA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                        .andReturn().getResponse().getStatus();
                statusReject.set(st);
            } catch (Exception e) {}
        });
        
        tApprove.start();
        tReject.start();
        latch.countDown();
        tApprove.join();
        tReject.join();
        
        assertThat(statusApprove.get() == 200 || statusApprove.get() == 409).isTrue();
        assertThat(statusReject.get() == 200 || statusReject.get() == 409).isTrue();
        assertThat(statusApprove.get() != statusReject.get()).isTrue();
        
        Invoice finalInvoice = invoiceRepository.findById(invoiceId).orElseThrow();
        assertThat(finalInvoice.getStatus() == InvoiceStatus.APPROVED || finalInvoice.getStatus() == InvoiceStatus.REJECTED).isTrue();
    }
    
    @Test
    void testRepeatedApprovalAfterTerminalState() throws Exception {
        WorkflowRule rule = new WorkflowRule();
        rule.setId(UUID.randomUUID());
        rule.setTenantId(tenantA.getId());
        rule.setName("Test Rule");
        rule.setActive(true);
        rule.setPriority(1);
        rule.setRequiredApprovals(1);
        rule.setMinimumAmount(new BigDecimal("100.00"));
        rule = workflowRuleRepository.save(rule);

        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setTenantId(tenantA.getId());
        invoice.setInvoiceNumber("INV-WF-REPEAT-1");
        invoice.setVendor(vendorA);
        invoice.setTotalAmount(new BigDecimal("500.00"));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setSubmitterId(submitterA.getId());
        invoice = invoiceRepository.save(invoice);

        mockMvc.perform(post("/api/invoices/" + invoice.getId() + "/submit")
                .header("Authorization", "Bearer " + tokenSubmitterA))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/invoices/" + invoice.getId() + "/workflow/initiate")
                .header("Authorization", "Bearer " + tokenSubmitterA))
                .andExpect(status().isOk());
                
        var instance = workflowInstanceRepository.findByInvoiceIdAndTenantId(invoice.getId(), tenantA.getId()).orElseThrow();

        ApprovalRequest request = new ApprovalRequest();
        request.setExpectedVersion(instance.getVersion());
        request.setComment("First approve");

        mockMvc.perform(post("/api/invoices/" + invoice.getId() + "/approval/approve")
                .header("Authorization", "Bearer " + tokenApproverA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
                
        // Now it's APPROVED. Try approving again
        mockMvc.perform(post("/api/invoices/" + invoice.getId() + "/approval/approve")
                .header("Authorization", "Bearer " + tokenApproverA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
