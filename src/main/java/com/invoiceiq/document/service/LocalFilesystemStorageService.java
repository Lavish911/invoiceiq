package com.invoiceiq.document.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFilesystemStorageService implements DocumentStorageService {

    private final Path storageDirectory;

    public LocalFilesystemStorageService(@Value("${app.storage.local.dir:./data/storage}") String storageDir) {
        this.storageDirectory = Paths.get(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Could not create storage directory", e);
        }
    }

    @Override
    public String storeFile(MultipartFile file, String tenantId) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            originalFilename = Paths.get(originalFilename).getFileName().toString();
        } else {
            originalFilename = "unknown";
        }
        String filename = UUID.randomUUID().toString() + "_" + originalFilename;
        Path tenantDir = this.storageDirectory.resolve(tenantId).normalize();
        
        try {
            if (!tenantDir.startsWith(this.storageDirectory)) {
                throw new SecurityException("Cannot store file outside of storage directory");
            }
            Files.createDirectories(tenantDir);
            Path targetLocation = tenantDir.resolve(filename).normalize();
            if (!targetLocation.startsWith(tenantDir)) {
                 throw new SecurityException("Cannot store file outside of tenant directory");
            }
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return tenantId + "/" + filename;
        } catch (IOException ex) {
            throw new RuntimeException("Could not store file " + filename + ". Please try again!", ex);
        }
    }

    @Override
    public byte[] getFile(String storageKey) {
        try {
            Path filePath = this.storageDirectory.resolve(storageKey).normalize();
            if (!filePath.startsWith(this.storageDirectory)) {
                throw new SecurityException("Cannot access file outside of storage directory");
            }
            return Files.readAllBytes(filePath);
        } catch (IOException ex) {
            throw new RuntimeException("File not found " + storageKey, ex);
        }
    }

    @Override
    public void deleteFile(String storageKey) {
        try {
            Path filePath = this.storageDirectory.resolve(storageKey).normalize();
            if (!filePath.startsWith(this.storageDirectory)) {
                throw new SecurityException("Cannot access file outside of storage directory");
            }
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            throw new RuntimeException("Could not delete file " + storageKey, ex);
        }
    }
}
