package com.exam_bank.exam_service.feature.upload.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUploadRequest {

    @Size(max = 2000)
    private String note;
}
