package com.invoiceiq.tenant.entity;

import com.invoiceiq.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tenant")
@Getter
@Setter
public class Tenant extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;
}
