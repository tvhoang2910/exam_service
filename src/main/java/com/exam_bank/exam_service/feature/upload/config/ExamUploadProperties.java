package com.exam_bank.exam_service.feature.upload.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "exam.upload")
public class ExamUploadProperties {

    @Min(60)
    @Max(3600)
    private int presignPutTtlSeconds = 900;

    @Min(60)
    @Max(3600)
    private int presignGetTtlSeconds = 600;

    @Min(1)
    @Max(50)
    private int maxPages = 20;

    @NotEmpty
    private List<String> allowedContentTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf");
}
