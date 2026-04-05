package com.exam_bank.exam_service.service;

import com.exam_bank.notification_service.dto.AdminAlertMessage;
import com.exam_bank.exam_service.feature.reporting.entity.ReportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminAlertPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${notification.exchange:notification.events}")
    private String notificationExchange;

    @Value("${notification.admin-alert-routing-key:notification.send.admin.alert}")
    private String adminAlertRoutingKey;

    public void publishExamSubmittedAlert(String userFullName, Long examId, String examTitle, Long attemptId) {
        AdminAlertMessage message = new AdminAlertMessage(
                "EXAM_SUBMITTED",
                "Bài thi mới được nộp",
                userFullName + " — " + examTitle,
                List.of("ADMIN", "CONTRIBUTOR"),
                "/admin/exams",
                Map.of("attemptId", attemptId, "examId", examId));
        rabbitTemplate.convertAndSend(notificationExchange, adminAlertRoutingKey, message);
        log.info("Published EXAM_SUBMITTED admin alert: attemptId={}, examId={}", attemptId, examId);
    }

    public void publishQuestionReportThresholdAlert(
            Long creatorUserId,
            Long examId,
            String examTitle,
            Long questionId,
            ReportType reportType,
            long totalOpenReports) {
        AdminAlertMessage message = new AdminAlertMessage(
                "QUESTION_REPORT_THRESHOLD",
                "Câu hỏi bị báo cáo nhiều lần",
                String.format("%s - Câu #%d đã bị báo cáo %d lần (%s)",
                        examTitle,
                        questionId,
                        totalOpenReports,
                        reportType),
                List.of(),
                "/reports",
                Map.of(
                        "targetUserId", creatorUserId,
                        "questionId", questionId,
                        "examId", examId,
                        "reportType", reportType.name(),
                        "totalOpenReports", totalOpenReports));
        rabbitTemplate.convertAndSend(notificationExchange, adminAlertRoutingKey, message);
        log.info("Published QUESTION_REPORT_THRESHOLD alert: creatorUserId={}, examId={}, questionId={}, reports={}",
                creatorUserId,
                examId,
                questionId,
                totalOpenReports);
    }
}
