package com.invoiceiq.notification.service;

import com.invoiceiq.invoice.entity.Invoice;
import com.invoiceiq.notification.entity.Notification;
import com.invoiceiq.notification.entity.NotificationChannel;
import com.invoiceiq.notification.entity.NotificationStatus;
import com.invoiceiq.notification.repository.NotificationRepository;
import com.invoiceiq.auth.entity.User;
import com.invoiceiq.tenant.context.TenantContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notificationRepository;

    @Transactional
    public void createNotification(Invoice invoice, User recipient, String recipientRole, NotificationChannel channel, String subject, String message, UUID tenantId) {
        Notification notification = new Notification();
        notification.setTenantId(tenantId);
        notification.setInvoice(invoice);
        notification.setRecipient(recipient);
        notification.setRecipientRole(recipientRole);
        notification.setChannel(channel);
        notification.setSubject(subject);
        notification.setMessage(message);
        notification.setStatus(NotificationStatus.PENDING);
        notification = notificationRepository.save(notification);

        // Schedule async delivery using JobRunr (Mocked delivery logic)
        final UUID notificationId = notification.getId();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // This simulates scheduling
                    // jobScheduler.enqueue(() -> sendNotification(notificationId, tenantId));
                    sendNotificationSync(notificationId, tenantId);
                }
            }
        );
    }

    public void sendNotificationSync(UUID notificationId, UUID tenantId) {
        try {
            TenantContext.setCurrentTenant(tenantId);
            Notification notification = notificationRepository.findById(notificationId).orElseThrow();
            // Simulate sending logic (e.g. email)
            log.info("Sending notification: {} to {}", notification.getSubject(), notification.getRecipientRole());
            
            notification.setStatus(NotificationStatus.SENT);
            notificationRepository.save(notification);
        } catch (Exception e) {
            log.error("Failed to send notification: {}", notificationId, e);
        } finally {
            TenantContext.clear();
        }
    }
}
