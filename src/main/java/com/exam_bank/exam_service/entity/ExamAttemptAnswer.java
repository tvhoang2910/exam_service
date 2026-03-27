package com.exam_bank.exam_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@Entity
@Table(name = "exam_attempt_answers", uniqueConstraints = {
        @UniqueConstraint(name = "uq_attempt_question", columnNames = { "attempt_id", "question_id" })
}, indexes = {
        @Index(name = "idx_attempt_answers_attempt", columnList = "attempt_id"),
        @Index(name = "idx_attempt_answers_question", columnList = "question_id")
})
public class ExamAttemptAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "selected_option_ids", length = 500)
    private String selectedOptionIds;

    @Column(name = "is_correct")
    @ColumnDefault("false")
    private Boolean isCorrect = false;

    @Column(name = "earned_score")
    @ColumnDefault("0")
    private Double earnedScore = 0.0;

    @Column(name = "max_score")
    @ColumnDefault("0")
    private Double maxScore = 0.0;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "answer_change_count")
    @ColumnDefault("0")
    private Integer answerChangeCount = 0;
}
