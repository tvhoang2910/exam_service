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
import lombok.extern.slf4j.Slf4j;
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
public class ExamAttemptController {

    private final ExamAttemptService examAttemptService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping("/attempts")
    public ResponseEntity<StartAttemptResponse> startAttempt(@Valid @RequestBody StartAttemptRequest request) {
        Long userId = authenticatedUserService.getCurrentUserId();
        StartAttemptResponse response = examAttemptService.startAttempt(request, userId);
        log.info("startAttempt: userId={}, examId={}, attemptId={}", userId, request.getExamId(), response.getAttemptId());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/attempts/{attemptId}/answers")
    public ResponseEntity<Void> saveAnswer(@PathVariable Long attemptId,
            @Valid @RequestBody SaveAttemptAnswerRequest request) {
        Long userId = authenticatedUserService.getCurrentUserId();
        examAttemptService.saveAnswer(attemptId, userId, request);
        log.debug("saveAnswer: userId={}, attemptId={}, questionId={}", userId, attemptId, request.getQuestionId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/attempts/{attemptId}/answers/batch")
    public ResponseEntity<Void> saveAnswersBatch(@PathVariable Long attemptId,
            @Valid @RequestBody SaveAttemptAnswersBatchRequest request) {
        Long userId = authenticatedUserService.getCurrentUserId();
        examAttemptService.saveAnswersBatch(attemptId, userId, request);
        log.info("saveAnswersBatch: userId={}, attemptId={}, batchSize={}", userId, attemptId, request.getAnswers().size());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<AttemptResultResponse> submitAttempt(@PathVariable Long attemptId) {
        Long userId = authenticatedUserService.getCurrentUserId();
        AttemptResultResponse response = examAttemptService.submitAttempt(attemptId, userId);
        log.info("submitAttempt: userId={}, attemptId={}, score={}/{}, percent={}%",
                userId, attemptId, response.getScoreRaw(), response.getScoreMax(), response.getScorePercent());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/attempts/{attemptId}/result")
    public ResponseEntity<AttemptResultResponse> getAttemptResult(@PathVariable Long attemptId) {
        Long userId = authenticatedUserService.getCurrentUserId();
        AttemptResultResponse response = examAttemptService.getAttemptResult(attemptId, userId);
        log.info("getAttemptResult: userId={}, attemptId={}, status={}, score={}/{}",
                userId,
                attemptId,
                response.getStatus(),
                response.getScoreRaw(),
                response.getScoreMax());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/me/attempts")
    public ResponseEntity<List<AttemptSummaryResponse>> getMyAttempts() {
        Long userId = authenticatedUserService.getCurrentUserId();
        List<AttemptSummaryResponse> history = examAttemptService.getAttemptHistory(userId);
        log.info("getMyAttempts: userId={}, historyCount={}", userId, history.size());
        return ResponseEntity.ok(history);
    }
}
