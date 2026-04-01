package com.exam_bank.exam_service.dto;

import java.time.Instant;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ExamSubmittedEvent {

    private Long attemptId;
    private Long userId;
    private Long examId;
    private String examTitle;
    private Instant submittedAt;
    private Double scoreRaw;
    private Double scoreMax;
    private Double scorePercent;
    private Long durationSeconds;
    private List<QuestionAnswered> questions;
    private List<TagInfo> examTags;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class QuestionAnswered {
        private Long questionId;
        private Boolean isCorrect;
        private Double earnedScore;
        private Double maxScore;
        private Long responseTimeMs;
        private Integer answerChangeCount;
        private Double difficulty;
        private String tagIds; // comma-separated
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class TagInfo {
        private Long tagId;
        private String tagName;
    }
}
