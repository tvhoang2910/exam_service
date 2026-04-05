package com.exam_bank.exam_service.feature.reporting.entity;

import com.exam_bank.exam_service.entity.BaseEntity;
import com.exam_bank.exam_service.entity.ExamAttempt;
import com.exam_bank.exam_service.entity.Question;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "question_reports", uniqueConstraints = {
        @UniqueConstraint(name = "uq_question_report_dedup", columnNames = { "question_id", "attempt_id",
                "reporter_id" })
}, indexes = {
        @Index(name = "idx_question_reports_question", columnList = "question_id"),
        @Index(name = "idx_question_reports_status", columnList = "status"),
        @Index(name = "idx_question_reports_reporter", columnList = "reporter_id")
})
public class QuestionReport extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 40)
    private ReportType reportType;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ReportStatus status = ReportStatus.REPORTED;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @Column(name = "reporter_id", nullable = false)
    private Long reporterId;
}
