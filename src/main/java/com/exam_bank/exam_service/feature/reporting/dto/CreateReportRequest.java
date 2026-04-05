package com.exam_bank.exam_service.feature.reporting.dto;

import com.exam_bank.exam_service.feature.reporting.entity.ReportType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReportRequest {

    @NotNull(message = "Loại báo cáo không được để trống")
    private ReportType reportType;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;
}
