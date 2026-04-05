package com.exam_bank.exam_service.feature.reporting.dto;

import com.exam_bank.exam_service.feature.reporting.entity.QuestionReport;
import com.exam_bank.exam_service.feature.reporting.entity.ReportStatus;
import com.exam_bank.exam_service.feature.reporting.entity.ReportType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
public class QuestionReportResponse {

    private Long id;
    private Long questionId;
    private String questionPreview;
    private Long attemptId;
    private Long examId;
    private String examTitle;
    private Long reporterId;
    private String reporterUsername;
    private ReportType reportType;
    private String reportTypeLabel;
    private String description;
    private ReportStatus status;
    private String statusLabel;
    private String resolutionNote;
    private Long resolvedBy;
    private String resolvedByUsername;
    private Instant resolvedAt;
    private Instant createdAt;

    public static QuestionReportResponse from(QuestionReport report) {
        return QuestionReportResponse.builder()
                .id(report.getId())
                .questionId(report.getQuestion().getId())
                .questionPreview(truncate(report.getQuestion().getContent(), 200))
                .attemptId(report.getAttempt().getId())
                .examId(report.getQuestion().getExam().getId())
                .examTitle(report.getQuestion().getExam().getTitle())
                .reporterId(report.getReporterId())
                .reporterUsername("User #" + report.getReporterId())
                .reportType(report.getReportType())
                .reportTypeLabel(getReportTypeLabel(report.getReportType()))
                .description(report.getDescription())
                .status(report.getStatus())
                .statusLabel(getStatusLabel(report.getStatus()))
                .resolutionNote(report.getResolutionNote())
                .resolvedBy(report.getResolvedBy())
                .resolvedByUsername(report.getResolvedBy() == null ? null : "User #" + report.getResolvedBy())
                .resolvedAt(report.getResolvedAt())
                .createdAt(report.getCreatedAt())
                .build();
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private static String getReportTypeLabel(ReportType type) {
        return switch (type) {
            case WRONG_ANSWER -> "Sai đáp án";
            case TYPO -> "Lỗi chính tả";
            case MISSING_INFORMATION -> "Thiếu thông tin";
            case INVALID_QUESTION -> "Đề sai";
            case OTHER -> "Khác";
        };
    }

    private static String getStatusLabel(ReportStatus status) {
        return switch (status) {
            case REPORTED -> "Mới";
            case REVIEWING -> "Đang xem";
            case RESOLVED -> "Đã xử lý";
            case REJECTED -> "Từ chối";
        };
    }
}
