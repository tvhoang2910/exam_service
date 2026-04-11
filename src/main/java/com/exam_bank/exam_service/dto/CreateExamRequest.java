package com.exam_bank.exam_service.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class CreateExamRequest {
    private String title;
    private String description;
    private Integer durationMinutes;
    private Integer passingScore;
    private Integer maxAttempts;
    private Boolean premium;
    private Integer teaserQuestionCount;
    private List<Long> tagIds;
    private List<String> newTags;
    private List<QuestionDto> questions;

    @Getter
    @Setter
    public static class QuestionDto {
        private String content;
        private String explanation;
        private Double scoreWeight;
        private List<OptionDto> options;
    }

    @Getter
    @Setter
    public static class OptionDto {
        private String content;
        private Boolean isCorrect;
    }
}