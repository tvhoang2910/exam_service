package com.exam_bank.exam_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaveAttemptAnswersBatchRequest {
    @NotEmpty
    private List<@Valid SaveAttemptAnswerRequest> answers;
}
