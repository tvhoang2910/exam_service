package com.exam_bank.exam_service.feature.upload.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitiateUploadRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 2000)
    private String description;

    @Min(1)
    @Max(20)
    private int pageCount;

    @NotBlank
    private String contentType;
}
