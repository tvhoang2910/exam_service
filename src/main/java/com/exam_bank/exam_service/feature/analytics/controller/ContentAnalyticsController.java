package com.exam_bank.exam_service.feature.analytics.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.exam_bank.exam_service.feature.analytics.dto.ExamAnalyticsSummaryDto;
import com.exam_bank.exam_service.feature.analytics.dto.QuestionAnalyticsDto;
import com.exam_bank.exam_service.feature.analytics.service.ContentAnalyticsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
@Slf4j
public class ContentAnalyticsController {

    private final ContentAnalyticsService analyticsService;

    @GetMapping("/questions/{questionId}")
    public ResponseEntity<QuestionAnalyticsDto> getQuestionAnalytics(@PathVariable Long questionId) {
        QuestionAnalyticsDto dto = analyticsService.getQuestionAnalytics(questionId);
        log.info("getQuestionAnalytics: questionId={}, attempts={}, correctRate={}%, difficulty={}",
                questionId, dto.getTotalAttempts(), dto.getCorrectRate(), dto.getDifficultyLabel());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/exams/{examId}")
    public ResponseEntity<ExamAnalyticsSummaryDto> getExamAnalytics(@PathVariable Long examId) {
        ExamAnalyticsSummaryDto dto = analyticsService.getExamAnalytics(examId);
        log.info("getExamAnalytics: examId={}, attempts={}, avgScore={}%, participants={}",
                examId, dto.getTotalAttempts(), dto.getAvgScorePercent(), dto.getUniqueParticipants());
        return ResponseEntity.ok(dto);
    }
}
