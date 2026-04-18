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
    private String uploadedByUserId; // Để biết ai là người up đề
    private String aiJsonResult;     // Chứa nguyên mảng JSON kết quả mà AI trả v
    private Boolean SuccessFlag;
    private String errorMessage;     // Chứa câu báo lỗi (nếu quá trình bên search_service thất bại)
}