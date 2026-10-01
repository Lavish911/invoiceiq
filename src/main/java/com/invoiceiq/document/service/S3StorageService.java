package com.invoiceiq.document.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements DocumentStorageService {

    private final S3Client s3Client;
    private final String bucketName;

    public S3StorageService(
            @Value("${app.storage.s3.bucket}") String bucketName,
            @Value("${app.storage.s3.endpoint:}") String endpoint,
            @Value("${app.storage.s3.region:us-east-1}") String region,
            @Value("${app.storage.s3.access-key:}") String accessKey,
            @Value("${app.storage.s3.secret-key:}") String secretKey
    ) {
        this.bucketName = bucketName;
        
        software.amazon.awssdk.services.s3.S3ClientBuilder s3Builder = S3Client.builder()
                .region(Region.of(region));
                
        if (!endpoint.isBlank()) {
            s3Builder.endpointOverride(URI.create(endpoint));
            s3Builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            s3Builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }

        this.s3Client = s3Builder.build();
    }

    @Override
    public String storeFile(MultipartFile file, String tenantId) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            originalFilename = Paths.get(originalFilename).getFileName().toString();
        } else {
            originalFilename = "unknown";
        }
        String filename = tenantId + "/" + UUID.randomUUID() + "_" + originalFilename;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(filename)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Could not read file " + filename, e);
        } catch (Exception e) {
            throw new RuntimeException("Could not store file to S3: " + filename, e);
        }
    }

    @Override
    public byte[] getFile(String storageKey) {
        try {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();
                    
            return s3Client.getObjectAsBytes(getRequest).asByteArray();
        } catch (Exception e) {
            throw new RuntimeException("File not found in S3: " + storageKey, e);
        }
    }

    @Override
    public void deleteFile(String storageKey) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build();
                    
            s3Client.deleteObject(deleteRequest);
        } catch (Exception e) {
            throw new RuntimeException("Could not delete file from S3: " + storageKey, e);
        }
    }
}
