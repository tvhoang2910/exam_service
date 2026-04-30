package com.exam_bank.exam_service.feature.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InitiateUploadResponse {
    private Long uploadId;
    private List<PresignedPut> pages;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PresignedPut {
        private int index;
        private String objectKey;
        private String url;
        private long expiresInSeconds;
    }
}
