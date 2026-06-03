package com.exam_bank.exam_service.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.exam_bank.exam_service.service.ExtractionResultService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/internal/extraction")
@RequiredArgsConstructor
public class ExtractionAdminController {

    private final ExtractionResultService extractionResultService;

    @PostMapping("/force-placeholder")
    public ResponseEntity<?> forcePlaceholder(@RequestBody Map<String, Object> body) {
        Object uploadObj = body.get("uploadRequestId");
        Object examObj = body.get("examId");
        if (uploadObj == null || examObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "uploadRequestId and examId are required"));
        }
        Long uploadId = Long.valueOf(String.valueOf(uploadObj));
        Long examId = Long.valueOf(String.valueOf(examObj));
        extractionResultService.createPlaceholderForUpload(uploadId, examId);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
