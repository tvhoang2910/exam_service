package com.exam_bank.exam_service.dto;

import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.entity.QuestionType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ExamResponse {
    private Long id;
    private String title;
    private String description;
    private Integer durationMinutes;
    private Integer passingScore;
    private Integer maxAttempts;
    private Integer totalQuestions;
    private Boolean premium;
    private Integer teaserQuestionCount;
    private Boolean premiumLocked;
    private OnlineExamStatus status;
    private Instant createdAt;
    private Instant modifiedAt;
    private List<TagDto> tags = new ArrayList<>();
    private List<QuestionResponse> questions = new ArrayList<>();

    @Getter
    @Setter
    public static class QuestionResponse {
        private Long id;
        private String content;
        private QuestionType questionType;
        private String explanation;
        private Double score;
        private Double scoreWeight;
        private String sampleAnswer;
        private String gradingGuide;
        private Question.Difficulty difficulty;
        private List<OptionResponse> options = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class OptionResponse {
        private Long id;
        private String content;
        private Boolean isCorrect;
    }
}
