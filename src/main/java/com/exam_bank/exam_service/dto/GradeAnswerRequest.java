package com.exam_bank.exam_service.dto;


import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class GradeAnswerRequest {
    @NotNull(message = "Điểm không được để trống")
    @Min(value = 0, message = "Điểm không được âm")
    private Double score;

    @JsonAlias("teacherFeedback")
    private String feedback; // Lời phê của giáo viên/contributor (có thể null)

    public String getTeacherFeedback() {
        return feedback;
    }

    public void setTeacherFeedback(String teacherFeedback) {
        this.feedback = teacherFeedback;
    }
}
