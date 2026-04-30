package com.exam_bank.exam_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
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

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
    public static class QuestionDto {
        private String content;
        private String explanation;
        private Double scoreWeight;
        private List<OptionDto> options;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
    public static class OptionDto {
        private String content;
        private Boolean isCorrect;
    }
}