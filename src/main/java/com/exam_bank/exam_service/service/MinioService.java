package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.config.properties.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

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
}