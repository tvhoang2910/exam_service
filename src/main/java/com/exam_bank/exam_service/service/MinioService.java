package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.config.properties.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public String uploadFile(MultipartFile file) {
        try {
            // Tự động tạo bucket nếu chưa tồn tại
            boolean isExist = minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioProperties.getBucketName()).build());
            if (!isExist) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucketName()).build());
                log.info("Bucket {} created successfully.", minioProperties.getBucketName());
            }

            // Sinh tên file độc nhất bằng UUID để tránh bị ghi đè khi upload trùng tên
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String objectName = UUID.randomUUID().toString() + extension;

            // Thực hiện đẩy file
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(minioProperties.getBucketName())
                                .object(objectName)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            log.info("File uploaded to MinIO successfully: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("Error occurred while uploading file to MinIO: ", e);
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    /**
     * Build deterministic object key for valet-key uploads.
     * Format: uploads/{uploaderId}/{uploadId}/page-{index}.{ext}
     */
    public String buildObjectKey(Long uploaderId, Long uploadId, int pageIndex, String contentType) {
        String ext = extensionForContentType(contentType);
        return String.format("uploads/%d/%d/page-%d%s", uploaderId, uploadId, pageIndex, ext);
    }

    /**
     * Generate a pre-signed PUT URL so the browser can upload directly to MinIO.
     * Client must send the same Content-Type header.
     */
    public String generatePresignedPutUrl(String objectKey, String contentType, int expirySeconds) {
        try {
            ensureBucketExists();
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucketName())
                            .object(objectKey)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned PUT url for key {}: {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned PUT url", e);
        }
    }

    /**
     * Generate a pre-signed GET URL for downloading/viewing an object.
     */
    public String generatePresignedGetUrl(String objectKey, int expirySeconds) {
        try {
            ensureBucketExists();
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucketName())
                            .object(objectKey)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned GET url for key {}: {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned GET url", e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean isExist = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(minioProperties.getBucketName()).build());
        if (!isExist) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioProperties.getBucketName()).build());
            log.info("Bucket {} created successfully.", minioProperties.getBucketName());
        }
    }

    private String extensionForContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }
}