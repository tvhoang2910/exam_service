package com.exam_bank.exam_service.feature.analytics.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.feature.analytics.dto.ExamAnalyticsSummaryDto;
import com.exam_bank.exam_service.feature.analytics.dto.QuestionAnalyticsDto;
import com.exam_bank.exam_service.feature.analytics.repository.ContentAnalyticsRepository;
import com.exam_bank.exam_service.feature.analytics.repository.ContentAnalyticsRepository.ExamStatProjection;
import com.exam_bank.exam_service.feature.analytics.repository.ContentAnalyticsRepository.QuestionStatProjection;
import com.exam_bank.exam_service.repository.OnlineExamRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ContentAnalyticsService {

    private final ContentAnalyticsRepository analyticsRepository;
    private final OnlineExamRepository examRepository;

    public QuestionAnalyticsDto getQuestionAnalytics(Long questionId) {
        QuestionStatProjection p = analyticsRepository.findQuestionStats(questionId);
        if (p == null) {
            return QuestionAnalyticsDto.builder()
                    .questionId(questionId)
                    .totalAttempts(0)
                    .correctRate(0.0)
                    .difficultyIndex(1.0)
                    .discriminationIndex(0.0)
                    .avgResponseTimeMs(0.0)
                    .difficultyLabel("Chưa có dữ liệu")
                    .build();
        }

        double correctRate = p.getCorrectRate() != null ? p.getCorrectRate() : 0.0;
        double difficultyIndex = Math.round((1.0 - correctRate / 100.0) * 100.0) / 100.0;

        return QuestionAnalyticsDto.builder()
                .questionId(p.getQuestionId())
                .totalAttempts(p.getTotalAttempts())
                .correctRate(correctRate)
                .difficultyIndex(difficultyIndex)
                .discriminationIndex(p.getDiscriminationIndex() != null ? p.getDiscriminationIndex() : 0.0)
                .avgResponseTimeMs(p.getAvgResponseTimeMs() != null ? p.getAvgResponseTimeMs() : 0.0)
                .difficultyLabel(labelDifficulty(correctRate))
                .build();
    }

    public ExamAnalyticsSummaryDto getExamAnalytics(Long examId) {
        ExamStatProjection p = analyticsRepository.findExamStats(examId);
        if (p == null) {
            return ExamAnalyticsSummaryDto.builder()
                    .examId(examId)
                    .totalAttempts(0)
                    .avgScorePercent(0.0)
                    .avgDurationSeconds(0.0)
                    .uniqueParticipants(0)
                    .passRate(0.0)
                    .build();
        }

        String title = examRepository.findById(examId)
                .map(OnlineExam::getTitle)
                .orElse("");

        return ExamAnalyticsSummaryDto.builder()
                .examId(p.getExamId())
                .examTitle(title)
                .totalAttempts(p.getTotalAttempts())
                .avgScorePercent(p.getAvgScorePercent() != null ? p.getAvgScorePercent() : 0.0)
                .avgDurationSeconds(p.getAvgDurationSeconds() != null ? p.getAvgDurationSeconds() : 0.0)
                .uniqueParticipants(p.getUniqueParticipants() != null ? p.getUniqueParticipants() : 0)
                .passRate(p.getPassRate() != null ? p.getPassRate() : 0.0)
                .build();
    }

    private String labelDifficulty(double correctRate) {
        if (correctRate >= 80) return "Dễ";
        if (correctRate >= 50) return "Trung bình";
        if (correctRate >= 20) return "Khó";
        return "Cực khó";
    }
}
