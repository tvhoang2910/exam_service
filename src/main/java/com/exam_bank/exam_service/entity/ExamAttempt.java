package com.exam_bank.exam_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "exam_attempts", indexes = {
        @Index(name = "idx_exam_attempts_exam_user", columnList = "exam_id,user_id"),
        @Index(name = "idx_exam_attempts_user", columnList = "user_id"),
        @Index(name = "idx_exam_attempts_status", columnList = "status")
})
public class ExamAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private OnlineExam exam;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExamAttemptStatus status = ExamAttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "score_raw")
    @ColumnDefault("0")
    private Double scoreRaw = 0.0;

    @Column(name = "score_max")
    @ColumnDefault("0")
    private Double scoreMax = 0.0;

    @Column(name = "score_percent")
    @ColumnDefault("0")
    private Double scorePercent = 0.0;

    @Column(name = "passed")
    @ColumnDefault("false")
    private Boolean passed = false;

    @Column(name = "source", nullable = false, length = 30)
    @ColumnDefault("'WEB'")
    private String source = "WEB";

    @Column(name = "client_version", length = 100)
    private String clientVersion;
}
