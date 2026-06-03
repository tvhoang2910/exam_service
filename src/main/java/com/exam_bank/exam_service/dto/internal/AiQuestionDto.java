package com.exam_bank.exam_service.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiQuestionDto {
    private String content;
    private String questionType;
    private String explanation;
    private String sampleAnswer;
    private String gradingGuide;
    private String difficulty;
    private Double score;
    private Double scoreWeight;
    private List<AiOptionDto> options;
}
