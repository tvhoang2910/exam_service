package com.exam_bank.exam_service.feature.upload.entity;

import com.exam_bank.exam_service.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "exam_upload_history", indexes = {
        @Index(name = "idx_exam_upload_history_request", columnList = "upload_request_id"),
        @Index(name = "idx_exam_upload_history_created_at", columnList = "created_at")
})
public class ExamUploadHistory extends BaseEntity {

    @Column(name = "upload_request_id", nullable = false)
    private Long uploadRequestId;

    @Column(name = "action", nullable = false, length = 40)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private ExamUploadStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private ExamUploadStatus newStatus;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
}
