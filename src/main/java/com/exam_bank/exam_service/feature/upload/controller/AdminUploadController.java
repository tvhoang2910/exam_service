package com.exam_bank.exam_service.feature.upload.controller;

import com.exam_bank.exam_service.feature.upload.dto.ExamUploadHistoryResponse;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadPageResponse;
import com.exam_bank.exam_service.feature.upload.dto.ExamUploadResponse;
import com.exam_bank.exam_service.feature.upload.dto.RejectUploadRequest;
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
@RequestMapping("/admin/uploads")
@RequiredArgsConstructor
@Slf4j
public class AdminUploadController {

    private final ExamUploadService uploadService;

    @GetMapping("/queue")
    public ResponseEntity<ExamUploadPageResponse> queue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(uploadService.listPendingQueue(page, size));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ExamUploadResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(uploadService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ExamUploadResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody RejectUploadRequest request) {
        return ResponseEntity.ok(uploadService.reject(id, request));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<ExamUploadHistoryResponse>> history(@PathVariable Long id) {
        return ResponseEntity.ok(uploadService.getHistory(id));
    }
}
