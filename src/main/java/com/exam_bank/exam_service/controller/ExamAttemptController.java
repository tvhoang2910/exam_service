package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.AttemptResultResponse;
import com.exam_bank.exam_service.dto.AttemptSummaryResponse;
import com.exam_bank.exam_service.dto.SaveAttemptAnswerRequest;
import com.exam_bank.exam_service.dto.SaveAttemptAnswersBatchRequest;
import com.exam_bank.exam_service.dto.StartAttemptRequest;
import com.exam_bank.exam_service.dto.StartAttemptResponse;
import com.exam_bank.exam_service.service.AuthenticatedUserService;
import com.exam_bank.exam_service.service.ExamAttemptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class ExamAttemptController {

    private final ExamAttemptService examAttemptService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping("/attempts")
    public ResponseEntity<StartAttemptResponse> startAttempt(@Valid @RequestBody StartAttemptRequest request) {
        Long userId = authenticatedUserService.getCurrentUserId();
        return ResponseEntity.ok(examAttemptService.startAttempt(request, userId));
    }

    @PutMapping("/attempts/{attemptId}/answers")
    public ResponseEntity<Void> saveAnswer(@PathVariable Long attemptId,
            @Valid @RequestBody SaveAttemptAnswerRequest request) {
        Long userId = authenticatedUserService.getCurrentUserId();
        examAttemptService.saveAnswer(attemptId, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/attempts/{attemptId}/answers/batch")
    public ResponseEntity<Void> saveAnswersBatch(@PathVariable Long attemptId,
            @Valid @RequestBody SaveAttemptAnswersBatchRequest request) {
        Long userId = authenticatedUserService.getCurrentUserId();
        examAttemptService.saveAnswersBatch(attemptId, userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<AttemptResultResponse> submitAttempt(@PathVariable Long attemptId) {
        Long userId = authenticatedUserService.getCurrentUserId();
        return ResponseEntity.ok(examAttemptService.submitAttempt(attemptId, userId));
    }

    @GetMapping("/attempts/{attemptId}/result")
    public ResponseEntity<AttemptResultResponse> getAttemptResult(@PathVariable Long attemptId) {
        Long userId = authenticatedUserService.getCurrentUserId();
        return ResponseEntity.ok(examAttemptService.getAttemptResult(attemptId, userId));
    }

    @GetMapping("/users/me/attempts")
    public ResponseEntity<List<AttemptSummaryResponse>> getMyAttempts() {
        Long userId = authenticatedUserService.getCurrentUserId();
        return ResponseEntity.ok(examAttemptService.getAttemptHistory(userId));
    }
}
