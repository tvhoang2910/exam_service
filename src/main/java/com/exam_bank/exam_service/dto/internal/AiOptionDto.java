package com.exam_bank.exam_service.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiOptionDto {
    private String content;

    @JsonProperty("isCorrect") // Đảm bảo Jackson map đúng chữ "isCorrect" từ JSON
    private boolean isCorrect;
}
