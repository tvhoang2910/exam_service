package com.exam_bank.exam_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "question_review_events", indexes = {
        @Index(name = "idx_qr_events_user_item", columnList = "user_id,item_id"),
        @Index(name = "idx_qr_events_attempt", columnList = "attempt_id")
})
public class QuestionReviewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "attempt_id", nullable = false)
    private Long attemptId;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "quality", nullable = false)
    private Integer quality;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "topic_tag_ids", length = 500)
    private String topicTagIds;

    @Column(name = "difficulty")
    private Double difficulty;

    @Column(name = "source", nullable = false, length = 50)
    private String source;
}
