package com.invoiceiq.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Per-tenant rolling upload quota counter. The row is locked
 * (PESSIMISTIC_WRITE) for the check-and-increment, which serializes
 * concurrent uploads of the same tenant and closes the check-then-act race.
 */
@Entity
@Table(name = "demo_quota")
@Getter
@Setter
public class DemoQuota {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "window_start", nullable = false)
    private OffsetDateTime windowStart = OffsetDateTime.now();

    @Column(name = "upload_count", nullable = false)
    private int uploadCount = 0;

    @Column(name = "bytes_total", nullable = false)
    private long bytesTotal = 0L;
}
