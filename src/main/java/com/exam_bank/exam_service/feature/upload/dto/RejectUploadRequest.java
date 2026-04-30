package com.exam_bank.exam_service.feature.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectUploadRequest {

    @NotBlank
    @Size(max = 1000)
    private String reason;
}
