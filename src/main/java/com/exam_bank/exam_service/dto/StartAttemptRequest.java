package com.exam_bank.exam_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartAttemptRequest {
    @NotNull
    private Long examId;

    private String clientVersion;
}
