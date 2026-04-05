package com.exam_bank.exam_service.feature.reporting.controller;

import com.exam_bank.exam_service.feature.reporting.dto.CreateReportRequest;
import com.exam_bank.exam_service.feature.reporting.dto.QuestionReportHistoryResponse;
import com.exam_bank.exam_service.feature.reporting.dto.QuestionReportResponse;
import com.exam_bank.exam_service.feature.reporting.dto.ReportQueuePageResponse;
import com.exam_bank.exam_service.feature.reporting.dto.ReportQueueItem;
import com.exam_bank.exam_service.feature.reporting.dto.ResolveReportRequest;
import com.exam_bank.exam_service.feature.reporting.service.QuestionReportService;
import com.exam_bank.exam_service.service.AuthenticatedUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
@Slf4j
public class QuestionReportController {

    private final QuestionReportService questionReportService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping("/attempts/{attemptId}/questions/{questionId}/reports")
    public ResponseEntity<QuestionReportResponse> createReport(
            @PathVariable Long attemptId,
            @PathVariable Long questionId,
            @Valid @RequestBody CreateReportRequest request) {
        Long userId = authenticatedUserService.getCurrentUserId();
        QuestionReportResponse response = questionReportService.createReport(questionId, attemptId, userId, request);
        log.info("createReport: userId={}, attemptId={}, questionId={}", userId, attemptId, questionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/me/reports")
    public ResponseEntity<List<QuestionReportResponse>> getMyReports() {
        Long userId = authenticatedUserService.getCurrentUserId();
        List<QuestionReportResponse> reports = questionReportService.getMyReports(userId);
        return ResponseEntity.ok(reports);
    }

    @GetMapping("/admin/reports")
    public ResponseEntity<ReportQueuePageResponse> getReportQueue(@PageableDefault(size = 20) Pageable pageable) {
        Long userId = authenticatedUserService.getCurrentUserId();
        Page<ReportQueueItem> page = questionReportService.getReportQueue(pageable, userId);
        return ResponseEntity.ok(ReportQueuePageResponse.from(page));
    }

    @GetMapping("/admin/reports/processed")
    public ResponseEntity<ReportQueuePageResponse> getProcessedReportQueue(
            @PageableDefault(size = 20) Pageable pageable) {
        Long userId = authenticatedUserService.getCurrentUserId();
        Page<ReportQueueItem> page = questionReportService.getProcessedReportQueue(pageable, userId);
        return ResponseEntity.ok(ReportQueuePageResponse.from(page));
    }

    @GetMapping("/admin/reports/questions/{questionId}")
    public ResponseEntity<List<QuestionReportResponse>> getQuestionReports(@PathVariable Long questionId) {
        Long userId = authenticatedUserService.getCurrentUserId();
        return ResponseEntity.ok(questionReportService.getReportsForQuestion(questionId, userId));
    }

    @GetMapping("/admin/reports/questions/{questionId}/history")
    public ResponseEntity<List<QuestionReportHistoryResponse>> getReportHistory(@PathVariable Long questionId) {
        Long userId = authenticatedUserService.getCurrentUserId();
        return ResponseEntity.ok(questionReportService.getReportHistoryForQuestion(questionId, userId));
    }

    @PutMapping("/admin/reports/questions/{questionId}/resolve")
    public ResponseEntity<Void> resolveReports(
            @PathVariable Long questionId,
            @Valid @RequestBody ResolveReportRequest request) {
        Long userId = authenticatedUserService.getCurrentUserId();
        questionReportService.resolveQuestionReports(questionId, userId, request);
        return ResponseEntity.noContent().build();
    }
}
