package com.exam_bank.exam_service.dto;

import com.exam_bank.exam_service.entity.ExamAttemptStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class AttemptSummaryResponse {
    private Long attemptId;
    private Long examId;
    private String examTitle;
    private ExamAttemptStatus status;
    private Instant startedAt;
    private Instant submittedAt;
    private Double scoreRaw;
    private Double scoreMax;
    private Double scorePercent;
    private Boolean passed;
}
