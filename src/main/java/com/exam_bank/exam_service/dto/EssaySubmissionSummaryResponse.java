package com.exam_bank.exam_service.dto;

import com.exam_bank.exam_service.entity.AnswerStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class EssaySubmissionSummaryResponse {
    private Long id;
    private Long answerId;
    private Long attemptId;
    private Long questionId;
    private Long studentId;
    private String studentName;
    private Long examId;
    private String examTitle;
    private Instant submittedAt;
    private Double score;
    private Double maxScore;
    private AnswerStatus status;
}
