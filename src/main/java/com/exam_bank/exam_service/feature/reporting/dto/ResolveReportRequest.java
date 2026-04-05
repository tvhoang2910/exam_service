package com.exam_bank.exam_service.feature.reporting.dto;

import com.exam_bank.exam_service.feature.reporting.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveReportRequest {

    @NotNull(message = "Trạng thái xử lý không được để trống")
    private ReportStatus status;

    @Size(max = 1000, message = "Ghi chú xử lý không được vượt quá 1000 ký tự")
    private String resolutionNote;

    private boolean unhideQuestion;
}
