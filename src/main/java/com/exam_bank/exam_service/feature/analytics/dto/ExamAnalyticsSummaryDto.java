package com.exam_bank.exam_service.feature.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamAnalyticsSummaryDto {
    private Long examId;
    private String examTitle;
    private Integer totalAttempts;
    private Double avgScorePercent;
    private Double avgDurationSeconds;
    private Integer uniqueParticipants;
    private Double passRate;
}
