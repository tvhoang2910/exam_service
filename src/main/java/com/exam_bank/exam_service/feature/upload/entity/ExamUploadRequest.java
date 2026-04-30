package com.exam_bank.exam_service.feature.upload.entity;

import com.exam_bank.exam_service.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "exam_upload_requests", indexes = {
        @Index(name = "idx_exam_upload_requests_status", columnList = "status"),
        @Index(name = "idx_exam_upload_requests_uploader", columnList = "uploader_id")
})
public class ExamUploadRequest extends BaseEntity {

    @Column(name = "uploader_id", nullable = false)
    private Long uploaderId;

    @Column(name = "uploader_role", nullable = false, length = 20)
    private String uploaderRole;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "page_count", nullable = false)
    private Integer pageCount;

    /** Comma-separated list of MinIO object keys in page order. */
    @Column(name = "object_keys", columnDefinition = "TEXT")
    private String objectKeys;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ExamUploadStatus status;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "extracted_exam_id")
    private Long extractedExamId;

    @Column(name = "extraction_error", columnDefinition = "TEXT")
    private String extractionError;

    @Transient
    public List<String> getKeys() {
        if (objectKeys == null || objectKeys.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(objectKeys.split(",")));
    }

    @Transient
    public void setKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            this.objectKeys = null;
        } else {
            this.objectKeys = String.join(",", keys);
        }
    }
}
