package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.entity.ExamAttempt;
import com.exam_bank.exam_service.entity.ExamAttemptStatus;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.feature.reporting.dto.CreateReportRequest;
import com.exam_bank.exam_service.feature.reporting.dto.ResolveReportRequest;
import com.exam_bank.exam_service.feature.reporting.entity.QuestionReport;
import com.exam_bank.exam_service.feature.reporting.entity.ReportStatus;
import com.exam_bank.exam_service.feature.reporting.entity.ReportType;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportHistoryRepository;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportRepository;
import com.exam_bank.exam_service.feature.reporting.service.QuestionReportService;
import com.exam_bank.exam_service.repository.ExamAttemptRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
        private ArgumentCaptor<QuestionReport> reportCaptor;

        private OnlineExam exam;
        private ExamAttempt submittedAttempt;
        private Question question;

        private static final Long USER_ID = 7L;
        private static final Long ATTEMPT_ID = 10L;
        private static final Long QUESTION_ID = 20L;

        @BeforeEach
        void setUp() {
                ReflectionTestUtils.setField(questionReportService, "autoHideThreshold", 5);

                exam = new OnlineExam();
                exam.setId(2L);
                exam.setTitle("Đề kiểm tra");
                exam.setCreatedBy("11");

                question = new Question();
                question.setId(QUESTION_ID);
                question.setExam(exam);
                question.setContent("2 + 2 = ?");
                question.setIsHidden(false);

                submittedAttempt = new ExamAttempt();
                submittedAttempt.setId(ATTEMPT_ID);
                submittedAttempt.setExam(exam);
                submittedAttempt.setUserId(USER_ID);
                submittedAttempt.setStatus(ExamAttemptStatus.SUBMITTED);
        }

        @Test
        void createReport_shouldSaveReportSuccessfully() {
                CreateReportRequest request = new CreateReportRequest();
                request.setReportType(ReportType.WRONG_ANSWER);
                request.setDescription("Đáp án đúng là B");

                when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                                .thenReturn(Optional.of(submittedAttempt));
                when(questionRepository.findByIdAndExamId(QUESTION_ID, exam.getId())).thenReturn(Optional.of(question));
                when(reportRepository.existsByQuestionIdAndAttemptIdAndReporterId(QUESTION_ID, ATTEMPT_ID, USER_ID))
                                .thenReturn(false);
                when(reportRepository.countByQuestionIdAndReportTypeAndStatusIn(
                                QUESTION_ID,
                                ReportType.WRONG_ANSWER,
                                List.of(ReportStatus.REPORTED, ReportStatus.REVIEWING))).thenReturn(1L);
                when(reportRepository.countByQuestionIdAndStatusIn(
                                QUESTION_ID,
                                List.of(ReportStatus.REPORTED, ReportStatus.REVIEWING))).thenReturn(1L);
                when(reportRepository.save(any(QuestionReport.class))).thenAnswer(invocation -> {
                        QuestionReport report = invocation.getArgument(0);
                        report.setId(101L);
                        return report;
                });

                var response = questionReportService.createReport(QUESTION_ID, ATTEMPT_ID, USER_ID, request);

                verify(reportRepository).save(reportCaptor.capture());
                QuestionReport saved = reportCaptor.getValue();
                assertThat(saved.getReportType()).isEqualTo(ReportType.WRONG_ANSWER);
                assertThat(saved.getDescription()).isEqualTo("Đáp án đúng là B");
                assertThat(saved.getStatus()).isEqualTo(ReportStatus.REPORTED);
                assertThat(response.getId()).isEqualTo(101L);
                assertThat(response.getQuestionId()).isEqualTo(QUESTION_ID);
        }

        @Test
        void createReport_shouldRejectDuplicateByAttempt() {
                CreateReportRequest request = new CreateReportRequest();
                request.setReportType(ReportType.TYPO);

                when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                                .thenReturn(Optional.of(submittedAttempt));
                when(questionRepository.findByIdAndExamId(QUESTION_ID, exam.getId())).thenReturn(Optional.of(question));
                when(reportRepository.existsByQuestionIdAndAttemptIdAndReporterId(QUESTION_ID, ATTEMPT_ID, USER_ID))
                                .thenReturn(true);

                assertThatThrownBy(() -> questionReportService.createReport(QUESTION_ID, ATTEMPT_ID, USER_ID, request))
                                .isInstanceOf(ResponseStatusException.class)
                                .hasMessageContaining("đã báo lỗi");

                verify(reportRepository, never()).save(any());
        }

        @Test
        void createReport_shouldRejectNonSubmittedAttempt() {
                submittedAttempt.setStatus(ExamAttemptStatus.IN_PROGRESS);
                CreateReportRequest request = new CreateReportRequest();
                request.setReportType(ReportType.OTHER);

                when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                                .thenReturn(Optional.of(submittedAttempt));

                assertThatThrownBy(() -> questionReportService.createReport(QUESTION_ID, ATTEMPT_ID, USER_ID, request))
                                .isInstanceOf(ResponseStatusException.class)
                                .hasMessageContaining("sau khi nộp bài");
        }

        @Test
        void createReport_shouldAutoHideWhenThresholdReached() {
                CreateReportRequest request = new CreateReportRequest();
                request.setReportType(ReportType.INVALID_QUESTION);

                when(attemptRepository.findByIdAndUserId(ATTEMPT_ID, USER_ID))
                                .thenReturn(Optional.of(submittedAttempt));
                when(questionRepository.findByIdAndExamId(QUESTION_ID, exam.getId())).thenReturn(Optional.of(question));
                when(reportRepository.existsByQuestionIdAndAttemptIdAndReporterId(QUESTION_ID, ATTEMPT_ID, USER_ID))
                                .thenReturn(false);
                when(reportRepository.countByQuestionIdAndReportTypeAndStatusIn(
                                QUESTION_ID,
                                ReportType.INVALID_QUESTION,
                                List.of(ReportStatus.REPORTED, ReportStatus.REVIEWING))).thenReturn(5L);
                when(reportRepository.countByQuestionIdAndStatusIn(
                                QUESTION_ID,
                                List.of(ReportStatus.REPORTED, ReportStatus.REVIEWING))).thenReturn(5L);
                when(reportRepository.save(any(QuestionReport.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));
                when(questionRepository.save(any(Question.class)))
                                .thenAnswer(invocation -> invocation.getArgument(0));

                questionReportService.createReport(QUESTION_ID, ATTEMPT_ID, USER_ID, request);

                assertThat(question.getIsHidden()).isTrue();
                verify(questionRepository).save(question);
                verify(adminAlertPublisher).publishQuestionReportThresholdAlert(
                                11L,
                                exam.getId(),
                                exam.getTitle(),
                                QUESTION_ID,
                                ReportType.INVALID_QUESTION,
                                5L);
        }

        @Test
        void getMyReports_shouldReturnMappedResults() {
                QuestionReport report = new QuestionReport();
                report.setId(1L);
                report.setQuestion(question);
                report.setAttempt(submittedAttempt);
                report.setReporterId(USER_ID);
                report.setReportType(ReportType.MISSING_INFORMATION);

                when(reportRepository.findDetailedByReporterIdOrderByCreatedAtDesc(USER_ID))
                                .thenReturn(List.of(report));

                var result = questionReportService.getMyReports(USER_ID);

                assertThat(result).hasSize(1);
                assertThat(result.getFirst().getReportType()).isEqualTo(ReportType.MISSING_INFORMATION);
        }

        @Test
        void getReportQueue_shouldAggregateQueueByQuestion() {
                QuestionReport latest = new QuestionReport();
                latest.setId(11L);
                latest.setQuestion(question);
                latest.setAttempt(submittedAttempt);
                latest.setReporterId(100L);
                latest.setReportType(ReportType.WRONG_ANSWER);
                latest.setStatus(ReportStatus.REPORTED);
                latest.setCreatedAt(Instant.now());

                when(reportRepository.findQueuedQuestionIdsByExamCreator(eq(String.valueOf(USER_ID)), any()))
                                .thenReturn(new PageImpl<>(List.of(QUESTION_ID), PageRequest.of(0, 20), 1));
                when(reportRepository.findDetailedByQuestionIdAndStatusInOrderByCreatedAtDesc(
                                QUESTION_ID,
                                List.of(ReportStatus.REPORTED, ReportStatus.REVIEWING))).thenReturn(List.of(latest));
                when(reportRepository.countByQuestionIdAndStatusInGroupedType(
                                QUESTION_ID,
                                List.of(ReportStatus.REPORTED, ReportStatus.REVIEWING)))
                                .thenReturn(Collections.singletonList(new Object[] { ReportType.WRONG_ANSWER, 3L }));
                when(reportRepository.countDistinctReporterByQuestionIdAndStatusIn(
                                QUESTION_ID,
                                List.of(ReportStatus.REPORTED, ReportStatus.REVIEWING))).thenReturn(2L);

                var page = questionReportService.getReportQueue(PageRequest.of(0, 20), USER_ID);

                assertThat(page.getTotalElements()).isEqualTo(1);
                assertThat(page.getContent().getFirst().getQuestionId()).isEqualTo(QUESTION_ID);
                assertThat(page.getContent().getFirst().getTotalReportCount()).isEqualTo(1);
        }

        @Test
        void getProcessedReportQueue_shouldIncludeResolvedQuestions() {
                QuestionReport latest = new QuestionReport();
                latest.setId(15L);
                latest.setQuestion(question);
                latest.setAttempt(submittedAttempt);
                latest.setReporterId(100L);
                latest.setReportType(ReportType.INVALID_QUESTION);
                latest.setStatus(ReportStatus.RESOLVED);
                latest.setCreatedAt(Instant.now());

                when(reportRepository.findProcessedQuestionIdsByExamCreator(eq(String.valueOf(USER_ID)), any()))
                                .thenReturn(new PageImpl<>(List.of(QUESTION_ID), PageRequest.of(0, 20), 1));
                when(reportRepository.findDetailedByQuestionIdAndStatusInOrderByCreatedAtDesc(eq(QUESTION_ID),
                                anyList()))
                                .thenReturn(List.of(latest));
                when(reportRepository.countByQuestionIdAndStatusInGroupedType(eq(QUESTION_ID), anyList()))
                                .thenReturn(Collections
                                                .singletonList(new Object[] { ReportType.INVALID_QUESTION, 1L }));
                when(reportRepository.countDistinctReporterByQuestionIdAndStatusIn(eq(QUESTION_ID), anyList()))
                                .thenReturn(1L);

                var page = questionReportService.getProcessedReportQueue(PageRequest.of(0, 20), USER_ID);

                assertThat(page.getTotalElements()).isEqualTo(1);
                assertThat(page.getContent().getFirst().getQuestionId()).isEqualTo(QUESTION_ID);
                assertThat(page.getContent().getFirst().getTotalReportCount()).isEqualTo(1);
        }

        @Test
        void resolveQuestionReports_shouldUpdateStatusAndUnhide() {
                question.setIsHidden(true);

                QuestionReport openReport = new QuestionReport();
                openReport.setId(9L);
                openReport.setQuestion(question);
                openReport.setAttempt(submittedAttempt);
                openReport.setReporterId(55L);
                openReport.setStatus(ReportStatus.REPORTED);

                ResolveReportRequest request = new ResolveReportRequest();
                request.setStatus(ReportStatus.RESOLVED);
                request.setResolutionNote("Đã sửa đáp án");
                request.setUnhideQuestion(true);

                when(questionRepository.existsByIdAndExamCreatedBy(QUESTION_ID, String.valueOf(USER_ID)))
                                .thenReturn(true);

                when(reportRepository.findDetailedByQuestionIdAndStatusInOrderByCreatedAtDesc(
                                QUESTION_ID,
                                List.of(ReportStatus.REPORTED, ReportStatus.REVIEWING)))
                                .thenReturn(List.of(openReport));
                when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
                when(historyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

                questionReportService.resolveQuestionReports(QUESTION_ID, USER_ID, request);

                verify(reportRepository).saveAll(anyList());
                verify(questionRepository).save(question);
                assertThat(openReport.getStatus()).isEqualTo(ReportStatus.RESOLVED);
                assertThat(question.getIsHidden()).isFalse();
        }

        @Test
        void resolveQuestionReports_shouldRejectInvalidStatus() {
                ResolveReportRequest request = new ResolveReportRequest();
                request.setStatus(ReportStatus.REPORTED);

                when(questionRepository.existsByIdAndExamCreatedBy(QUESTION_ID, String.valueOf(USER_ID)))
                                .thenReturn(true);

                assertThatThrownBy(() -> questionReportService.resolveQuestionReports(QUESTION_ID, USER_ID, request))
                                .isInstanceOf(ResponseStatusException.class)
                                .hasMessageContaining("không hợp lệ");
        }

        @Test
        void getReportsForQuestion_shouldRejectWhenQuestionDoesNotBelongToCurrentCreator() {
                when(questionRepository.existsByIdAndExamCreatedBy(QUESTION_ID, String.valueOf(USER_ID)))
                                .thenReturn(false);

                assertThatThrownBy(() -> questionReportService.getReportsForQuestion(QUESTION_ID, USER_ID))
                                .isInstanceOf(ResponseStatusException.class)
                                .hasMessageContaining("Không tìm thấy câu hỏi cần xử lý");

                verify(reportRepository, never()).findDetailedByQuestionIdOrderByCreatedAtDesc(any());
        }
}
