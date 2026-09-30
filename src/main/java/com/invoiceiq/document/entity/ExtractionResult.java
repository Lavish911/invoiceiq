package com.invoiceiq.document.entity;

import com.invoiceiq.common.entity.BaseTenantEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "extraction_result")
@Getter
@Setter
public class ExtractionResult extends BaseTenantEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Document document;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExtractionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_data", columnDefinition = "jsonb")
    private Map<String, Object> extractedData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "confidence_scores", columnDefinition = "jsonb")
    private Map<String, Double> confidenceScores;

    @Column(name = "error_message")
    private String errorMessage;

    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;
}
