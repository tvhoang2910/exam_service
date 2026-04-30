package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.message.AdminAlertMessage;
import com.exam_bank.exam_service.feature.reporting.entity.ReportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
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

        public void publishQuestionReportResolvedAlert(
                        List<Long> reporterUserIds,
                        Long examId,
                        String examTitle,
                        Long questionId,
                        int resolvedReportCount,
                        String resolutionNote) {
                if (reporterUserIds == null || reporterUserIds.isEmpty()) {
                        log.info("Skip QUESTION_REPORT_RESOLVED alert because no reporters were provided: questionId={}",
                                        questionId);
                        return;
                }

                String safeExamTitle = (examTitle == null || examTitle.isBlank()) ? "Đề thi" : examTitle;
                String body = String.format("%s - Câu #%d bạn đã báo lỗi đã được xử lý", safeExamTitle, questionId);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("targetUserIds", reporterUserIds);
                metadata.put("questionId", questionId);
                metadata.put("resolvedReportCount", resolvedReportCount);
                if (examId != null) {
                        metadata.put("examId", examId);
                }
                if (resolutionNote != null && !resolutionNote.isBlank()) {
                        metadata.put("resolutionNote", resolutionNote);
                }

                AdminAlertMessage message = new AdminAlertMessage(
                                "QUESTION_REPORT_RESOLVED",
                                "Báo cáo câu hỏi đã được xử lý",
                                body,
                                List.of(),
                                "/dashboard",
                                metadata);
                rabbitTemplate.convertAndSend(notificationExchange, adminAlertRoutingKey, message);

                log.info(
                                "Published QUESTION_REPORT_RESOLVED alert: questionId={}, examId={}, reporterCount={}, resolvedReportCount={}",
                                questionId,
                                examId,
                                reporterUserIds.size(),
                                resolvedReportCount);
        }

        public void publishUploadSubmittedAlert(Long uploadId, String title, Long uploaderId, String uploaderName) {
                try {
                        String displayName = (uploaderName == null || uploaderName.isBlank())
                                        ? ("User #" + uploaderId)
                                        : uploaderName;
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("uploadId", uploadId);
                        metadata.put("uploaderId", uploaderId);
                        metadata.put("title", title);
                        AdminAlertMessage message = new AdminAlertMessage(
                                        "EXAM_UPLOAD_SUBMITTED",
                                        "Đề thi mới chờ duyệt",
                                        displayName + " đã gửi đề: " + title,
                                        List.of("ADMIN", "CONTRIBUTOR"),
                                        "/admin/uploads",
                                        metadata);
                        rabbitTemplate.convertAndSend(notificationExchange, adminAlertRoutingKey, message);
                        log.info("Published EXAM_UPLOAD_SUBMITTED alert: uploadId={}, uploaderId={}", uploadId,
                                        uploaderId);
                } catch (AmqpException ex) {
                        log.error("Failed to publish EXAM_UPLOAD_SUBMITTED alert for uploadId={}: {}", uploadId,
                                        ex.getMessage(), ex);
                }
        }

        public void publishUploadApprovedAlert(Long uploadId, String title, Long ownerUserId, Long reviewerId) {
                try {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("uploadId", uploadId);
                        metadata.put("targetUserId", ownerUserId);
                        metadata.put("reviewerId", reviewerId);
                        metadata.put("title", title);
                        AdminAlertMessage message = new AdminAlertMessage(
                                        "EXAM_UPLOAD_APPROVED",
                                        "Đề thi đã được duyệt",
                                        "Đề \"" + title + "\" đã được duyệt và đang trích xuất.",
                                        List.of(),
                                        "/uploads",
                                        metadata);
                        rabbitTemplate.convertAndSend(notificationExchange, adminAlertRoutingKey, message);
                        log.info("Published EXAM_UPLOAD_APPROVED alert: uploadId={}, ownerUserId={}, reviewerId={}",
                                        uploadId, ownerUserId, reviewerId);
                } catch (AmqpException ex) {
                        log.error("Failed to publish EXAM_UPLOAD_APPROVED alert for uploadId={}: {}", uploadId,
                                        ex.getMessage(), ex);
                }
        }

        public void publishUploadRejectedAlert(Long uploadId, String title, Long ownerUserId, Long reviewerId,
                        String reason) {
                try {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("uploadId", uploadId);
                        metadata.put("targetUserId", ownerUserId);
                        metadata.put("reviewerId", reviewerId);
                        metadata.put("title", title);
                        if (reason != null) {
                                metadata.put("reason", reason);
                        }
                        AdminAlertMessage message = new AdminAlertMessage(
                                        "EXAM_UPLOAD_REJECTED",
                                        "Đề thi bị từ chối",
                                        "Đề \"" + title + "\" đã bị từ chối.",
                                        List.of(),
                                        "/uploads",
                                        metadata);
                        rabbitTemplate.convertAndSend(notificationExchange, adminAlertRoutingKey, message);
                        log.info("Published EXAM_UPLOAD_REJECTED alert: uploadId={}, ownerUserId={}", uploadId,
                                        ownerUserId);
                } catch (AmqpException ex) {
                        log.error("Failed to publish EXAM_UPLOAD_REJECTED alert for uploadId={}: {}", uploadId,
                                        ex.getMessage(), ex);
                }
        }

        public void publishSelfUploadAudit(Long uploadId, String title, Long uploaderId, String uploaderRole) {
                try {
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("uploadId", uploadId);
                        metadata.put("uploaderId", uploaderId);
                        metadata.put("uploaderRole", uploaderRole);
                        metadata.put("title", title);
                        AdminAlertMessage message = new AdminAlertMessage(
                                        "EXAM_SELF_UPLOADED",
                                        "Đề thi được tự đăng",
                                        uploaderRole + " #" + uploaderId + " đã tự đăng đề: " + title,
                                        List.of("ADMIN"),
                                        "/admin/uploads",
                                        metadata);
                        rabbitTemplate.convertAndSend(notificationExchange, adminAlertRoutingKey, message);
                        log.info("Published EXAM_SELF_UPLOADED audit: uploadId={}, uploaderId={}", uploadId,
                                        uploaderId);
                } catch (AmqpException ex) {
                        log.error("Failed to publish EXAM_SELF_UPLOADED audit for uploadId={}: {}", uploadId,
                                        ex.getMessage(), ex);
                }
        }
}
