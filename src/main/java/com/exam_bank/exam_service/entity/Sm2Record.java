package com.exam_bank.exam_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "sm2_records",
    uniqueConstraints = @UniqueConstraint(name = "uk_sm2_user_question", columnNames = {"user_id", "question_id"}),
    indexes = {
        @Index(name = "idx_sm2_user", columnList = "user_id"),
        @Index(name = "idx_sm2_next_review", columnList = "next_review_at"),
        @Index(name = "idx_sm2_question", columnList = "question_id")
    })
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Sm2Record {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "easiness_factor", nullable = false)
    private Double easinessFactor = 2.5;

    @Column(name = "repetition", nullable = false)
    private Integer repetition = 0;

    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays = 0;

    @Column(name = "next_review_at")
    private Instant nextReviewAt;

    @Column(name = "last_quality")
    private Integer lastQuality;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
