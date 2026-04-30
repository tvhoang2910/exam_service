package com.exam_bank.exam_service.dto.internal;

import lombok.Data;
import java.util.List;

@Data
public class AiQuestionDto {
    private String content;
    private String explanation;
    private Double scoreWeight;
    private List<AiOptionDto> options;
}