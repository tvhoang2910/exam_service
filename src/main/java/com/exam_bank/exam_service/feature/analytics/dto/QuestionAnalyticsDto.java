package com.exam_bank.exam_service.feature.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionAnalyticsDto {
    private Long questionId;
    private Integer totalAttempts;
    private Double correctRate;
    private Double difficultyIndex;
    private Double discriminationIndex;
    private Double avgResponseTimeMs;
    private String difficultyLabel;
}
