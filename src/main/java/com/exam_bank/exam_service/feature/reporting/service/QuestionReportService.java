package com.exam_bank.exam_service.feature.reporting.service;

import com.exam_bank.exam_service.entity.ExamAttempt;
import com.exam_bank.exam_service.entity.ExamAttemptStatus;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.feature.reporting.dto.CreateReportRequest;
import com.exam_bank.exam_service.feature.reporting.dto.QuestionReportHistoryResponse;
import com.exam_bank.exam_service.feature.reporting.dto.QuestionReportResponse;
import com.exam_bank.exam_service.feature.reporting.dto.ReportQueueItem;
import com.exam_bank.exam_service.feature.reporting.dto.ResolveReportRequest;
import com.exam_bank.exam_service.feature.reporting.entity.QuestionReport;
import com.exam_bank.exam_service.feature.reporting.entity.QuestionReportHistory;
import com.exam_bank.exam_service.feature.reporting.entity.QuestionReportHistory.HistoryAction;
import com.exam_bank.exam_service.feature.reporting.entity.ReportStatus;
import com.exam_bank.exam_service.feature.reporting.entity.ReportType;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportHistoryRepository;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportRepository;
import com.exam_bank.exam_service.repository.ExamAttemptRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import com.exam_bank.exam_service.service.AdminAlertPublisher;
import com.exam_bank.exam_service.service.ExamAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuestionReportService {

    private static final List<ReportStatus> OPEN_STATUSES = List.of(ReportStatus.REPORTED, ReportStatus.REVIEWING);
    private static final List<ReportStatus> ALL_STATUSES = List.of(ReportStatus.values());

    private final QuestionReportRepository reportRepository;
    private final QuestionReportHistoryRepository historyRepository;
    private final QuestionRepository questionRepository;
    private final ExamAttemptRepository attemptRepository;
    private final ExamAuditService examAuditService;
    private final AdminAlertPublisher adminAlertPublisher;

    @Value("${app.reporting.auto-hide-threshold:5}")
    private int autoHideThreshold;

    @Transactional
    public QuestionReportResponse createReport(Long questionId, Long attemptId, Long userId,
            CreateReportRequest request) {
        log.info("createReport: userId={}, questionId={}, attemptId={}, reportType={}",
                userId, questionId, attemptId, request.getReportType());

        ExamAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy lần làm bài"));

        if (attempt.getStatus() != ExamAttemptStatus.SUBMITTED
                && attempt.getStatus() != ExamAttemptStatus.AUTO_SUBMITTED) {
            throw new ResponseStatusException(BAD_REQUEST, "Chỉ có thể báo lỗi sau khi nộp bài");
        }

        Question question = questionRepository.findByIdAndExamId(questionId, attempt.getExam().getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Không tìm thấy câu hỏi trong đề này"));

        if (reportRepository.existsByQuestionIdAndAttemptIdAndReporterId(questionId, attemptId, userId)) {
            throw new ResponseStatusException(CONFLICT, "Bạn đã báo lỗi câu hỏi này ở lần làm bài này");
        }

        QuestionReport report = new QuestionReport();
        report.setQuestion(question);
        report.setAttempt(attempt);
        report.setReporterId(userId);
        report.setReportType(request.getReportType());
        report.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        report.setStatus(ReportStatus.REPORTED);

        QuestionReport saved = reportRepository.save(report);
        applyAutoHideIfNeeded(question, request.getReportType());

        return QuestionReportResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<QuestionReportResponse> getMyReports(Long userId) {
        return reportRepository.findDetailedByReporterIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(QuestionReportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<ReportQueueItem> getReportQueue(Pageable pageable, Long ownerUserId) {
        Page<Long> queuedQuestionIds = reportRepository.findQueuedQuestionIdsByExamCreator(
                String.valueOf(ownerUserId),
                pageable);
        if (queuedQuestionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<ReportQueueItem> content = queuedQuestionIds.getContent().stream()
                .map(questionId -> buildQueueItem(questionId, OPEN_STATUSES))
                .toList();

        return new PageImpl<>(content, pageable, queuedQuestionIds.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<ReportQueueItem> getProcessedReportQueue(Pageable pageable, Long ownerUserId) {
        Page<Long> processedQuestionIds = reportRepository.findProcessedQuestionIdsByExamCreator(
                String.valueOf(ownerUserId),
                pageable);
        if (processedQuestionIds.isEmpty()) {
            return Page.empty(pageable);
        }

        List<ReportQueueItem> content = processedQuestionIds.getContent().stream()
                .map(questionId -> buildQueueItem(questionId, ALL_STATUSES))
                .toList();

        return new PageImpl<>(content, pageable, processedQuestionIds.getTotalElements());
    }

    @Transactional(readOnly = true)
    public List<QuestionReportResponse> getReportsForQuestion(Long questionId, Long ownerUserId) {
        assertQuestionOwnedByUser(questionId, ownerUserId);
        return reportRepository.findDetailedByQuestionIdOrderByCreatedAtDesc(questionId)
                .stream()
                .map(QuestionReportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QuestionReportHistoryResponse> getReportHistoryForQuestion(Long questionId, Long ownerUserId) {
        assertQuestionOwnedByUser(questionId, ownerUserId);
        return historyRepository.findByQuestionIdOrderByProcessedAtDesc(questionId)
                .stream()
                .map(QuestionReportHistoryResponse::from)
                .toList();
    }

    @Transactional
    public void resolveQuestionReports(Long questionId, Long resolvedBy, ResolveReportRequest request) {
        assertQuestionOwnedByUser(questionId, resolvedBy);

        if (request.getStatus() == ReportStatus.REPORTED) {
            throw new ResponseStatusException(BAD_REQUEST, "Trạng thái xử lý không hợp lệ");
        }

        List<QuestionReport> openReports = reportRepository.findDetailedByQuestionIdAndStatusInOrderByCreatedAtDesc(
                questionId,
                OPEN_STATUSES);
        if (openReports.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Không có báo cáo đang chờ xử lý cho câu hỏi này");
        }

        String resolutionNote = request.getResolutionNote() == null ? null : request.getResolutionNote().trim();
        Instant resolvedAt = Instant.now();

        for (QuestionReport report : openReports) {
            report.setStatus(request.getStatus());
            report.setResolutionNote(resolutionNote);
            report.setResolvedBy(resolvedBy);
            report.setResolvedAt(resolvedAt);
        }
        reportRepository.saveAll(openReports);

        // Log resolution action in history (one entry per resolution)
        QuestionReport firstReport = openReports.getFirst();
        QuestionReportHistory history = new QuestionReportHistory();
        history.setReport(firstReport); // FK points to first report
        history.setAction(request.getStatus() == ReportStatus.RESOLVED
                ? HistoryAction.RESOLVED
                : HistoryAction.REJECTED);
        history.setPreviousStatus(ReportStatus.REPORTED);
        history.setNewStatus(request.getStatus());
        history.setNote(resolutionNote);
        history.setProcessedBy(resolvedBy);
        history.setProcessedAt(resolvedAt);
        historyRepository.save(history);

        if (request.isUnhideQuestion()) {
            questionRepository.findById(questionId).ifPresent(question -> {
                question.setIsHidden(false);
                questionRepository.save(question);
                log.info("resolveQuestionReports: question unhidden: questionId={}", questionId);
                examAuditService.log(
                        ExamAuditService.ACTION_QUESTION_UNHIDDEN,
                        resolvedBy,
                        null,
                        ExamAuditService.TARGET_QUESTION,
                        questionId,
                        null,
                        "Bỏ ẩn câu hỏi sau khi xử lý báo cáo");
            });
        }

        log.info("resolveQuestionReports: questionId={}, handledReports={}, status={}",
                questionId, openReports.size(), request.getStatus());

        String action = request.getStatus() == ReportStatus.RESOLVED
                ? ExamAuditService.ACTION_REPORT_RESOLVED
                : ExamAuditService.ACTION_REPORT_REJECTED;
        examAuditService.log(
                action,
                resolvedBy,
                null,
                ExamAuditService.TARGET_QUESTION,
                questionId,
                null,
                request.getStatus() == ReportStatus.RESOLVED
                        ? "Đã duyệt và xử lý báo cáo: " + openReports.size() + " báo cáo"
                        : "Đã từ chối báo cáo: " + openReports.size() + " báo cáo");
    }

    private void applyAutoHideIfNeeded(Question question, ReportType reportType) {
        long sameTypeCount = reportRepository.countByQuestionIdAndReportTypeAndStatusIn(
                question.getId(),
                reportType,
                OPEN_STATUSES);

        long totalOpenReports = reportRepository.countByQuestionIdAndStatusIn(question.getId(), OPEN_STATUSES);
        if (totalOpenReports == autoHideThreshold) {
            notifyExamCreatorThresholdReached(question, reportType, totalOpenReports);
        }

        if (sameTypeCount >= autoHideThreshold && !Boolean.TRUE.equals(question.getIsHidden())) {
            question.setIsHidden(true);
            questionRepository.save(question);

            String truncatedContent = question.getContent() != null && question.getContent().length() > 200
                    ? question.getContent().substring(0, 200) + "..."
                    : question.getContent();
            examAuditService.log(
                    ExamAuditService.ACTION_QUESTION_AUTO_HIDDEN,
                    0L,
                    "SYSTEM",
                    ExamAuditService.TARGET_QUESTION,
                    question.getId(),
                    truncatedContent,
                    "Tự động ẩn do " + sameTypeCount + " báo cáo loại " + reportType);

            // Log auto-hide in history (link to the latest open report for FK integrity)
            List<QuestionReport> reports = reportRepository.findDetailedByQuestionIdAndStatusInOrderByCreatedAtDesc(
                    question.getId(), OPEN_STATUSES);
            if (!reports.isEmpty()) {
                QuestionReport latestReport = reports.getFirst();
                QuestionReportHistory history = new QuestionReportHistory();
                history.setReport(latestReport);
                history.setAction(HistoryAction.AUTO_HIDDEN);
                history.setPreviousStatus(latestReport.getStatus());
                history.setNewStatus(latestReport.getStatus());
                history.setProcessedBy(0L); // system action
                history.setProcessedAt(Instant.now());
                history.setNote("Tự động ẩn do " + sameTypeCount + " báo cáo cùng loại");
                historyRepository.save(history);
            }

            log.info("applyAutoHideIfNeeded: auto-hidden questionId={}, reportType={}, threshold={}, currentCount={}",
                    question.getId(), reportType, autoHideThreshold, sameTypeCount);
        }
    }

    private void notifyExamCreatorThresholdReached(Question question, ReportType reportType, long totalOpenReports) {
        String creator = question.getExam().getCreatedBy();
        if (creator == null || creator.isBlank() || "system".equalsIgnoreCase(creator)) {
            log.warn("notifyExamCreatorThresholdReached: cannot resolve creator for examId={}, questionId={}",
                    question.getExam().getId(), question.getId());
            return;
        }

        try {
            Long creatorUserId = Long.parseLong(creator.trim());
            adminAlertPublisher.publishQuestionReportThresholdAlert(
                    creatorUserId,
                    question.getExam().getId(),
                    question.getExam().getTitle(),
                    question.getId(),
                    reportType,
                    totalOpenReports);
        } catch (NumberFormatException ex) {
            log.warn("notifyExamCreatorThresholdReached: invalid createdBy='{}' for examId={}, questionId={}",
                    creator, question.getExam().getId(), question.getId());
        }
    }

    private void assertQuestionOwnedByUser(Long questionId, Long ownerUserId) {
        boolean ownsQuestion = questionRepository.existsByIdAndExamCreatedBy(questionId, String.valueOf(ownerUserId));
        if (!ownsQuestion) {
            throw new ResponseStatusException(NOT_FOUND, "Không tìm thấy câu hỏi cần xử lý");
        }
    }

    private ReportQueueItem buildQueueItem(Long questionId, List<ReportStatus> statuses) {
        List<QuestionReport> reports = reportRepository.findDetailedByQuestionIdAndStatusInOrderByCreatedAtDesc(
                questionId,
                statuses);
        if (reports.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Queue item không còn tồn tại");
        }

        QuestionReport latest = reports.getFirst();
        List<Object[]> groupedTypeCounts = reportRepository.countByQuestionIdAndStatusInGroupedType(questionId,
                statuses);
        Map<String, Integer> typeCountMap = new HashMap<>();
        for (Object[] row : groupedTypeCounts) {
            ReportType type = (ReportType) row[0];
            int count = ((Number) row[1]).intValue();
            typeCountMap.put(type.name(), count);
        }

        ReportType topType = typeCountMap.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> ReportType.valueOf(entry.getKey()))
                .orElse(ReportType.OTHER);

        String questionPreview = latest.getQuestion().getContent();
        if (questionPreview != null && questionPreview.length() > 160) {
            questionPreview = questionPreview.substring(0, 160) + "...";
        }

        return ReportQueueItem.builder()
                .questionId(questionId)
                .questionPreview(questionPreview)
                .examId(latest.getQuestion().getExam().getId())
                .examTitle(latest.getQuestion().getExam().getTitle())
                .topReportType(topType)
                .topReportTypeLabel(toTypeLabel(topType))
                .totalReportCount(reports.size())
                .uniqueReportersCount(
                        (int) reportRepository.countDistinctReporterByQuestionIdAndStatusIn(questionId, statuses))
                .reportTypeCounts(typeCountMap)
                .latestReportedAt(latest.getCreatedAt())
                .build();
    }

    private String toTypeLabel(ReportType type) {
        return switch (type) {
            case WRONG_ANSWER -> "Sai đáp án";
            case TYPO -> "Lỗi chính tả";
            case MISSING_INFORMATION -> "Thiếu thông tin";
            case INVALID_QUESTION -> "Đề sai";
            case OTHER -> "Khác";
        };
    }
}
