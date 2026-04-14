package com.exam_bank.exam_service.feature.reporting.service;

import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.feature.reporting.dto.ResolveReportRequest;
import com.exam_bank.exam_service.feature.reporting.entity.QuestionReport;
import com.exam_bank.exam_service.feature.reporting.entity.QuestionReportHistory;
import com.exam_bank.exam_service.feature.reporting.entity.ReportStatus;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportHistoryRepository;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportRepository;
import com.exam_bank.exam_service.repository.ExamAttemptRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import com.exam_bank.exam_service.service.AdminAlertPublisher;
import com.exam_bank.exam_service.service.ExamAuditService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuestionReportService resolve flow")
class QuestionReportServiceTest {

        @Mock
        private QuestionReportRepository reportRepository;

        @Mock
        private QuestionReportHistoryRepository historyRepository;

        @Mock
        private QuestionRepository questionRepository;

        @Mock
        private ExamAttemptRepository attemptRepository;

        @Mock
        private ExamAuditService examAuditService;

        @Mock
        private AdminAlertPublisher adminAlertPublisher;

        @InjectMocks
        private QuestionReportService questionReportService;

        @Captor
        private ArgumentCaptor<List<Long>> reporterIdsCaptor;

        @Test
        @DisplayName("resolveQuestionReports sends notification to distinct reporters when status is RESOLVED")
        void resolveQuestionReportsSendsNotificationToDistinctReportersWhenResolved() {
                Long questionId = 91L;
                Long resolvedBy = 77L;

                List<QuestionReport> openReports = new ArrayList<>(List.of(
                                buildReport(questionId, 81L, "Mock Exam", 1001L),
                                buildReport(questionId, 81L, "Mock Exam", 1002L),
                                buildReport(questionId, 81L, "Mock Exam", 1001L)));

                when(questionRepository.existsByIdAndExamCreatedBy(questionId, String.valueOf(resolvedBy)))
                                .thenReturn(true);
                when(reportRepository.findDetailedByQuestionIdAndStatusInOrderByCreatedAtDesc(eq(questionId),
                                anyList()))
                                .thenReturn(openReports);
                when(reportRepository.saveAll(openReports)).thenReturn(openReports);
                when(historyRepository.save(any(QuestionReportHistory.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ResolveReportRequest request = new ResolveReportRequest();
                request.setStatus(ReportStatus.RESOLVED);
                request.setResolutionNote("Da cap nhat dap an");
                request.setUnhideQuestion(false);

                questionReportService.resolveQuestionReports(questionId, resolvedBy, request);

                verify(adminAlertPublisher).publishQuestionReportResolvedAlert(
                                reporterIdsCaptor.capture(),
                                eq(81L),
                                eq("Mock Exam"),
                                eq(questionId),
                                eq(3),
                                eq("Da cap nhat dap an"));

                assertThat(reporterIdsCaptor.getValue()).containsExactly(1001L, 1002L);
                assertThat(openReports)
                                .allSatisfy(report -> {
                                        assertThat(report.getStatus()).isEqualTo(ReportStatus.RESOLVED);
                                        assertThat(report.getResolvedBy()).isEqualTo(resolvedBy);
                                        assertThat(report.getResolvedAt()).isNotNull();
                                        assertThat(report.getResolutionNote()).isEqualTo("Da cap nhat dap an");
                                });
        }

        @Test
        @DisplayName("resolveQuestionReports does not notify reporters when status is REJECTED")
        void resolveQuestionReportsDoesNotNotifyWhenRejected() {
                Long questionId = 93L;
                Long resolvedBy = 88L;

                List<QuestionReport> openReports = new ArrayList<>(List.of(
                                buildReport(questionId, 82L, "Mock Exam 2", 2001L),
                                buildReport(questionId, 82L, "Mock Exam 2", 2002L)));

                when(questionRepository.existsByIdAndExamCreatedBy(questionId, String.valueOf(resolvedBy)))
                                .thenReturn(true);
                when(reportRepository.findDetailedByQuestionIdAndStatusInOrderByCreatedAtDesc(eq(questionId),
                                anyList()))
                                .thenReturn(openReports);
                when(reportRepository.saveAll(openReports)).thenReturn(openReports);
                when(historyRepository.save(any(QuestionReportHistory.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                ResolveReportRequest request = new ResolveReportRequest();
                request.setStatus(ReportStatus.REJECTED);
                request.setResolutionNote("Không đủ bằng chứng");
                request.setUnhideQuestion(false);

                questionReportService.resolveQuestionReports(questionId, resolvedBy, request);

                verify(adminAlertPublisher, never()).publishQuestionReportResolvedAlert(
                                any(), any(), any(), any(), anyInt(), any());
                assertThat(openReports)
                                .allSatisfy(report -> assertThat(report.getStatus()).isEqualTo(ReportStatus.REJECTED));
        }

        private QuestionReport buildReport(Long questionId, Long examId, String examTitle, Long reporterId) {
                OnlineExam exam = new OnlineExam();
                exam.setId(examId);
                exam.setTitle(examTitle);

                Question question = new Question();
                question.setId(questionId);
                question.setExam(exam);

                QuestionReport report = new QuestionReport();
                report.setQuestion(question);
                report.setReporterId(reporterId);
                report.setStatus(ReportStatus.REPORTED);
                return report;
        }
}
