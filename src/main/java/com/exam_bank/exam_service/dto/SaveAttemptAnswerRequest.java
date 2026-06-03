package com.exam_bank.exam_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaveAttemptAnswerRequest {
    @NotNull
    private Long questionId;

    private Long selectedOptionId;

    private List<Long> selectedOptionIds;

    private String essayAnswer;

    private String textAnswer;

    private Long responseTimeMs;

    private Integer answerChangeCount;
}
