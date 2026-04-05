package com.exam_bank.exam_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "questions")
public class Question extends BaseEntity {

    public enum Difficulty {
        EASY("Dễ"), // ≥80% correctRate
        MEDIUM("Trung bình"), // ≥50%
        HARD("Khó"), // ≥20%
        VERY_HARD("Cực khó"); // <20%

        private final String label;

        Difficulty(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private OnlineExam exam;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "score_weight")
    private Double scoreWeight = 1.0;

    @Column(name = "difficulty", length = 20)
    @Enumerated(EnumType.STRING)
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Column(name = "is_hidden", nullable = false)
    private Boolean isHidden = false;
}