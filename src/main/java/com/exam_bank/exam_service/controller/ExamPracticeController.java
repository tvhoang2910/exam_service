package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.service.ExamAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/exams/public")
@CrossOrigin(origins = "http://localhost:5173")
@RequiredArgsConstructor
@Slf4j
public class ExamPracticeController {

    private final ExamAttemptService examAttemptService;

    @GetMapping("/{examId}/attempt-view")
    public ResponseEntity<ExamResponse> getAttemptView(@PathVariable Long examId) {
        log.info("getAttemptView: examId={}", examId);
        return ResponseEntity.ok(examAttemptService.getAttemptView(examId));
    }
}
