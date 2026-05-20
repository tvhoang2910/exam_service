package com.exam_bank.exam_service.feature.upload.controller;

import com.exam_bank.exam_service.feature.upload.dto.*;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import com.exam_bank.exam_service.feature.upload.service.ExamUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    // =========================================================================
    // API DANH CHO CONTRIBUTOR VA ADMIN (NGHIEP VU DUYET DE)
    // =========================================================================

    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMIN')")
    @GetMapping("/pending")
    public ResponseEntity<ExamUploadPageResponse> getPendingUploads(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Gọi đúng tên hàm listPendingQueue trong Service
        return ResponseEntity.ok(uploadService.listPendingQueue(page, size));
    }

    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMIN')")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ExamUploadResponse> approveUpload(@PathVariable Long id) {

        // Service đã tự động lấy ID người duyệt và trả về ExamUploadResponse
        ExamUploadResponse response = uploadService.approve(id);
        log.info("approveUpload: uploadId={}", id);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMIN')")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ExamUploadResponse> rejectUpload(
            @PathVariable Long id,
            @Valid @RequestBody RejectUploadRequest request) {

        // Truyền thẳng request vào Service
        ExamUploadResponse response = uploadService.reject(id, request);
        log.info("rejectUpload: uploadId={}, reason={}", id, request.getReason());
        return ResponseEntity.ok(response);
    }
}
