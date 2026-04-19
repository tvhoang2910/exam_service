package com.exam_bank.exam_service.feature.upload.controller;

import com.exam_bank.exam_service.feature.upload.dto.CompleteUploadRequest;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadHistoryResponse;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadPageResponse;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadResponse;
import com.exam_bank.exam_service.feature.upload.dto.InitiateUploadRequest;
import com.exam_bank.exam_service.feature.upload.dto.InitiateUploadResponse;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import com.exam_bank.exam_service.feature.upload.service.ExamUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/uploads")
@RequiredArgsConstructor
@Slf4j
public class ExamUploadController {

    private final ExamUploadService uploadService;

    @PostMapping("/initiate")
    public ResponseEntity<InitiateUploadResponse> initiate(@Valid @RequestBody InitiateUploadRequest request) {
        return ResponseEntity.ok(uploadService.initiateUpload(request));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ExamUploadResponse> complete(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) CompleteUploadRequest request) {
        return ResponseEntity.ok(uploadService.completeUpload(id, request));
    }

    @GetMapping("/mine")
    public ResponseEntity<ExamUploadPageResponse> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ExamUploadStatus status) {
        return ResponseEntity.ok(uploadService.listMyUploads(page, size, status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExamUploadResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(uploadService.getDetail(id));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<ExamUploadHistoryResponse>> history(@PathVariable Long id) {
        return ResponseEntity.ok(uploadService.getHistory(id));
    }
}
