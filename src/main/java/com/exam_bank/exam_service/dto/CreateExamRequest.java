package com.exam_bank.exam_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.exam_bank.exam_service.entity.QuestionType;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor  // Bắt buộc phải có cho Jackson 3.x
@AllArgsConstructor // Tốt cho việc testing
@ToString           // Để log.info() in ra nội dung thay vì địa chỉ ô nhớ
public class CreateExamRequest {
    private String title;
    private String description;
    private Integer durationMinutes;
    private Integer passingScore;
    private Integer maxAttempts;
    private Boolean premium;
    private Integer teaserQuestionCount;
    private List<Long> tagIds;

    // "tags" là tên ngắn gọn cho FE, "newTags" là tên cũ của bạn. Jackson sẽ nhận cả 2.
    @JsonProperty("tags")
    @JsonAlias({"newTags", "tags"})
    private List<String> newTags;

    private List<QuestionDto> questions;

    @Getter @Setter @NoArgsConstructor @ToString
    public static class QuestionDto {
        private String content;
        private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;
        private String explanation;
        private Double score;
        private Double scoreWeight;
        private String sampleAnswer;
        private String gradingGuide;
        private List<OptionDto> options;

        public QuestionDto(String content, String explanation, Double scoreWeight, List<OptionDto> options) {
            this.content = content;
            this.explanation = explanation;
            this.scoreWeight = scoreWeight;
            this.options = options;
            this.questionType = QuestionType.MULTIPLE_CHOICE;
        }

        public QuestionDto(
                String content,
                QuestionType questionType,
                String explanation,
                Double score,
                Double scoreWeight,
                String sampleAnswer,
                String gradingGuide,
                List<OptionDto> options) {
            this.content = content;
            this.questionType = questionType == null ? QuestionType.MULTIPLE_CHOICE : questionType;
            this.explanation = explanation;
            this.score = score;
            this.scoreWeight = scoreWeight;
            this.sampleAnswer = sampleAnswer;
            this.gradingGuide = gradingGuide;
            this.options = options;
        }
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
    public static class OptionDto {
        private String content;
        private Boolean isCorrect;
    }
}
