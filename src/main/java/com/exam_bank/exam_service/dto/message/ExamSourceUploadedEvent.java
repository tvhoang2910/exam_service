package com.exam_bank.exam_service.dto.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExamSourceUploadedEvent {
    private Long examId;
    private String fileObjectName;
    private String originalFileName;
    private String uploadedByUserId; // Cực kỳ quan trọng để định danh người tạo câu hỏi sau này
}