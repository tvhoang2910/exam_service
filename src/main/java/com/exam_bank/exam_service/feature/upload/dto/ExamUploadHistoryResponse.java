package com.exam_bank.exam_service.feature.upload.dto;

import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamUploadHistoryResponse {
    private Long id;
    private String action;
    private ExamUploadStatus previousStatus;
    private ExamUploadStatus newStatus;
    private Long actorId;
    private String actorRole;
    private String note;
    private Instant createdAt;
}
