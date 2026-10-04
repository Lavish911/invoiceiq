package com.invoiceiq.demo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Marks demo-tenant rows that are permanent seed data. The cleanup job deletes
 * demo-tenant records that are NOT registered here (user-created demo content).
 * Plain entity on purpose: it must not be subject to the tenant Hibernate
 * filter, and every query carries an explicit tenant predicate.
 */
@Entity
@Table(name = "demo_seed")
@IdClass(DemoSeed.DemoSeedId.class)
@Getter
@Setter
public class DemoSeed {

    @Id
    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Id
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public static class DemoSeedId implements Serializable {
        private String entityType;
        private UUID entityId;

        public DemoSeedId() {
        }

        public DemoSeedId(String entityType, UUID entityId) {
            this.entityType = entityType;
            this.entityId = entityId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DemoSeedId that)) {
                return false;
            }
            return Objects.equals(entityType, that.entityType)
                    && Objects.equals(entityId, that.entityId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entityType, entityId);
        }
    }
}
