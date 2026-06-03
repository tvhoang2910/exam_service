package com.exam_bank.exam_service.feature.upload.dto;

import com.exam_bank.exam_service.entity.Question.Difficulty;
import com.exam_bank.exam_service.entity.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateQuestionRequest {

    @NotNull(message = "ID của đề thi không được để trống")
    private Long examId;

    @NotBlank(message = "Nội dung câu hỏi không được để trống")
    private String content;

    private QuestionType questionType = QuestionType.MULTIPLE_CHOICE;

    private String explanation; // Lời giải thích (có thể null)

    private Double score;

    private Double scoreWeight = 1.0; // Điểm mặc định là 1.0

    private String sampleAnswer;

    private String gradingGuide;

    @NotNull(message = "Mức độ khó không được để trống")
    private Difficulty difficulty;

    // Danh sách các đáp án (Dành cho câu hỏi trắc nghiệm)
    private List<OptionDto> options;

    @Data
    public static class OptionDto {
        @NotBlank(message = "Nội dung đáp án không được để trống")
        private String content;

        @NotNull(message = "Phải xác định đáp án này đúng hay sai")
        private Boolean isCorrect;
    }
}
