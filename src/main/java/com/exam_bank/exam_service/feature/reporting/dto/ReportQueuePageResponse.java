package com.exam_bank.exam_service.feature.reporting.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record ReportQueuePageResponse(
        List<ReportQueueItem> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static ReportQueuePageResponse from(Page<ReportQueueItem> page) {
        return new ReportQueuePageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
