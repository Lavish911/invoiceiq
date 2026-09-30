package com.invoiceiq.document.dto;

import lombok.Data;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class ExtractionResponse {
    private String status;
    
    @JsonProperty("extracted_data")
    private Map<String, Object> extractedData;
    
    @JsonProperty("confidence_scores")
    private Map<String, Double> confidenceScores;
    
    @JsonProperty("error_message")
    private String errorMessage;
}
