package com.exam_bank.exam_service.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiExtractionResultEvent {
    private Long examId;
    private String uploadedByUserId;
    private String aiJsonResult;
    private Boolean successFlag;
    private String errorMessage;
    private Long uploadRequestId;
    private Integer pageCount;
    private Long timestamp;
}
