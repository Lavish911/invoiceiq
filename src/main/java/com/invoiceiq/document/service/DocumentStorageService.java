package com.invoiceiq.document.service;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorageService {
    String storeFile(MultipartFile file, String tenantId);
    byte[] getFile(String storageKey);
    void deleteFile(String storageKey);
}
