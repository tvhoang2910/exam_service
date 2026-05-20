package com.exam_bank.exam_service.controller;

import com.exam_bank.exam_service.dto.CreateQuestionRequest;
import com.exam_bank.exam_service.service.AuthenticatedUserService;
import com.exam_bank.exam_service.service.QuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
@Slf4j
public class QuestionController {

    private final QuestionService questionService;
    private final AuthenticatedUserService authenticatedUserService;

    @PreAuthorize("hasAnyRole('CONTRIBUTOR', 'ADMIN')")
    @PostMapping
    public ResponseEntity<Void> createQuestion(@Valid @RequestBody CreateQuestionRequest request) {
        Long contributorId = authenticatedUserService.getCurrentUserId();
        questionService.createQuestion(request, contributorId);
        log.info("createQuestion: contributorId={}, examId={}", contributorId, request.getExamId());
        return ResponseEntity.ok().build();
    }
}