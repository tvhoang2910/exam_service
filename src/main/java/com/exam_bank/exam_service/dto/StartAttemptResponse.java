package com.exam_bank.exam_service.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class StartAttemptResponse {
    private Long attemptId;
    private Long examId;
    private Instant startedAt;
    private Instant expiresAt;
    private Integer durationMinutes;
    private Integer maxAttempts;
}
