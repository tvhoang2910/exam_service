package com.exam_bank.exam_service.dto;

import com.exam_bank.exam_service.entity.ExamAttemptStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class AttemptResultResponse {
    private Long attemptId;
    private Long examId;
    private String examTitle;
    private ExamAttemptStatus status;
    private Instant startedAt;
    private Instant submittedAt;
    private Long durationSeconds;
    private Double scoreRaw;
    private Double scoreMax;
    private Double scorePercent;
    private Integer passingScore;
    private Boolean passed;
    private List<QuestionResult> questionResults = new ArrayList<>();

    @Getter
    @Setter
    public static class QuestionResult {
        private Long questionId;
        private String content;
        private Double maxScore;
        private Double earnedScore;
        private Boolean correct;
        private List<OptionResult> options = new ArrayList<>();
        private List<Long> selectedOptionIds = new ArrayList<>();
        private List<Long> correctOptionIds = new ArrayList<>();
        private Long responseTimeMs;
        private Integer answerChangeCount;
    }

    @Getter
    @Setter
    public static class OptionResult {
        private Long id;
        private String content;
    }
}
