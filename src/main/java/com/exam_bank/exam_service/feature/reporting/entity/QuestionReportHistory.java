package com.exam_bank.exam_service.feature.reporting.entity;

import com.exam_bank.exam_service.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "question_report_history", indexes = {
    @Index(name = "idx_report_history_report", columnList = "report_id"),
    @Index(name = "idx_report_history_created", columnList = "created_at")
})
public class QuestionReportHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private QuestionReport report;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private HistoryAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 40)
    private ReportStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 40)
    private ReportStatus newStatus;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "processed_by", nullable = false)
    private Long processedBy;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public enum HistoryAction {
        RESOLVED,
        REJECTED,
        MARKED_REVIEWING,
        UNHIDE_QUESTION,
        AUTO_HIDDEN
    }
}
