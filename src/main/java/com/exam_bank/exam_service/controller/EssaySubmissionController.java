package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.EssaySubmissionDetailResponse;
import com.exam_bank.exam_service.dto.EssaySubmissionSummaryResponse;
import com.exam_bank.exam_service.dto.GradeAnswerRequest;
import com.exam_bank.exam_service.service.AuthenticatedUserService;
import com.exam_bank.exam_service.service.ExamAttemptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/essay-submissions")
@RequiredArgsConstructor
@Slf4j
public class EssaySubmissionController {

    private final ExamAttemptService examAttemptService;
    private final AuthenticatedUserService authenticatedUserService;

    @PreAuthorize("hasRole('CONTRIBUTOR')")
    @GetMapping("/pending")
    public ResponseEntity<List<EssaySubmissionSummaryResponse>> getPendingEssaySubmissions() {
        Long contributorId = authenticatedUserService.getCurrentUserId();
        List<EssaySubmissionSummaryResponse> submissions =
                examAttemptService.getPendingEssaySubmissions(contributorId);
        log.info("getPendingEssaySubmissions: contributorId={}, count={}", contributorId, submissions.size());
        return ResponseEntity.ok(submissions);
    }

    @PreAuthorize("hasRole('CONTRIBUTOR')")
    @GetMapping("/{id}")
    public ResponseEntity<EssaySubmissionDetailResponse> getEssaySubmission(@PathVariable Long id) {
        Long contributorId = authenticatedUserService.getCurrentUserId();
        EssaySubmissionDetailResponse response = examAttemptService.getEssaySubmission(id, contributorId);
        log.info("getEssaySubmission: contributorId={}, submissionId={}", contributorId, id);
        return ResponseEntity.ok(response);
    }

    @PreAuthorize("hasRole('CONTRIBUTOR')")
    @PutMapping("/{id}/grade")
    public ResponseEntity<EssaySubmissionDetailResponse> gradeEssaySubmission(
            @PathVariable Long id,
            @Valid @RequestBody GradeAnswerRequest request) {
        Long contributorId = authenticatedUserService.getCurrentUserId();
        EssaySubmissionDetailResponse response = examAttemptService.gradeEssaySubmission(id, contributorId, request);
        log.info("gradeEssaySubmission: contributorId={}, submissionId={}, score={}",
                contributorId, id, request.getScore());
        return ResponseEntity.ok(response);
    }
}
