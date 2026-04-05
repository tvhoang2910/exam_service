package com.exam_bank.exam_service.feature.reporting.dto;

import com.exam_bank.exam_service.feature.reporting.entity.ReportType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.Map;

@Getter
@Setter
@Builder
public class ReportQueueItem {

    private Long questionId;
    private String questionPreview;
    private Long examId;
    private String examTitle;
    private ReportType topReportType;
    private String topReportTypeLabel;
    private int totalReportCount;
    private int uniqueReportersCount;
    private Map<String, Integer> reportTypeCounts;
    private Instant latestReportedAt;
}
