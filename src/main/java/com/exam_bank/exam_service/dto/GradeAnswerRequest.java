package com.exam_bank.exam_service.dto;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GradeAnswerRequest {
    @NotNull(message = "Điểm không được để trống")
    @Min(value = 0, message = "Điểm không được âm")
    private Double score;
    private String teacherFeedback; // Lời phê của giáo viên (có thể null)
}