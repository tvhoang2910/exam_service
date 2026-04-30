package com.exam_bank.exam_service.feature.upload.entity;

public enum ExamUploadStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    EXTRACTING,
    EXTRACTED,
    EXTRACT_FAILED,
    SELF_UPLOADED
}
