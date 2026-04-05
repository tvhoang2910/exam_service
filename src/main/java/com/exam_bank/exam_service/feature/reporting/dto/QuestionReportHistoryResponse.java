package com.exam_bank.exam_service.feature.reporting.dto;

import com.exam_bank.exam_service.feature.reporting.entity.QuestionReportHistory;
import com.exam_bank.exam_service.feature.reporting.entity.ReportStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class QuestionReportHistoryResponse {
    private Long id;
    private String action;
    private String actionLabel;
    private ReportStatus previousStatus;
    private ReportStatus newStatus;
    private String note;
    private Long processedBy;
    private Instant processedAt;

    public static QuestionReportHistoryResponse from(QuestionReportHistory h) {
        return QuestionReportHistoryResponse.builder()
                .id(h.getId())
                .action(h.getAction().name())
                .actionLabel(toActionLabel(h.getAction()))
                .previousStatus(h.getPreviousStatus())
                .newStatus(h.getNewStatus())
                .note(h.getNote())
                .processedBy(h.getProcessedBy())
                .processedAt(h.getProcessedAt())
                .build();
    }

    private static String toActionLabel(QuestionReportHistory.HistoryAction action) {
        return switch (action) {
            case RESOLVED -> "Đã xử lý";
            case REJECTED -> "Từ chối";
            case MARKED_REVIEWING -> "Đánh dấu đang xem xét";
            case UNHIDE_QUESTION -> "Bỏ ẩn câu hỏi";
            case AUTO_HIDDEN -> "Tự động ẩn";
        };
    }
}
