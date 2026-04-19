package com.exam_bank.exam_service.feature.upload.dto;

import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamUploadResponse {
    private Long id;
    private Long uploaderId;
    private String uploaderRole;
    private String title;
    private String description;
    private Integer pageCount;
    private String contentType;
    private ExamUploadStatus status;
    private String rejectionReason;
    private Long reviewedBy;
    private Instant reviewedAt;
    private Long extractedExamId;
    private String extractionError;
    private Instant createdAt;
    private Instant modifiedAt;
    private List<String> objectKeys;
    /** Presigned GET urls for each page — only populated on detail fetch. */
    private List<String> viewUrls;
}
