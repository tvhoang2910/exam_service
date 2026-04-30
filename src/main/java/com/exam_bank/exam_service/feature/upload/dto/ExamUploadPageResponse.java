package com.exam_bank.exam_service.feature.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamUploadPageResponse {
    private List<ExamUploadResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static ExamUploadPageResponse from(Page<ExamUploadResponse> page) {
        return new ExamUploadPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
