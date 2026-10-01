package com.invoiceiq.document.service;

import com.invoiceiq.document.dto.ExtractionResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Service
public class PythonExtractionClient {

    private final RestTemplate restTemplate;
    private final String ocrServiceUrl;
    private final String aiServiceToken;

    public PythonExtractionClient(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${app.ocr.service.url:http://localhost:8000}") String ocrServiceUrl,
            @Value("${AI_SERVICE_TOKEN:default_dev_token}") String aiServiceToken,
            @Value("${app.ocr.service.connect-timeout:5}") long connectTimeout,
            @Value("${app.ocr.service.read-timeout:60}") long readTimeout) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(connectTimeout))
                .setReadTimeout(Duration.ofSeconds(readTimeout))
                .build();
        this.ocrServiceUrl = ocrServiceUrl;
        this.aiServiceToken = aiServiceToken;
    }

    public ExtractionResponse extractInvoiceData(byte[] fileBytes, String filename, String tenantId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        headers.set("Authorization", "Bearer " + aiServiceToken);
        headers.set("X-Tenant-ID", tenantId);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        String extractEndpoint = ocrServiceUrl.endsWith("/") ? ocrServiceUrl + "extract" : ocrServiceUrl + "/extract";
        ResponseEntity<ExtractionResponse> response = restTemplate.postForEntity(
                extractEndpoint,
                requestEntity,
                ExtractionResponse.class
        );

        return response.getBody();
    }
}
