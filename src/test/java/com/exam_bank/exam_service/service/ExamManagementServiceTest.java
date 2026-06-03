package com.exam_bank.exam_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.OnlineExamSource;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadRequest;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import com.exam_bank.exam_service.feature.upload.repository.ExamUploadRequestRepository;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportHistoryRepository;
import com.exam_bank.exam_service.feature.reporting.repository.QuestionReportRepository;
import com.exam_bank.exam_service.repository.ExamAttemptAnswerRepository;
import com.exam_bank.exam_service.repository.ExamAttemptRepository;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.repository.QuestionOptionRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import com.exam_bank.exam_service.repository.QuestionReviewEventRepository;
import com.exam_bank.exam_service.repository.Sm2RecordRepository;
import com.exam_bank.exam_service.repository.TagRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamManagementService Unit Tests")
class ExamManagementServiceTest {

    @Mock
    private OnlineExamRepository examRepo;

    @Mock
    private ExamAttemptRepository examAttemptRepo;

    @Mock
    private QuestionRepository questionRepo;

    @Mock
    private QuestionOptionRepository optionRepo;

    @Mock
    private Sm2RecordRepository sm2RecordRepo;

    @Mock
    private ExamAttemptAnswerRepository examAttemptAnswerRepo;

    @Mock
    private QuestionReviewEventRepository questionReviewEventRepo;

    @Mock
    private QuestionReportRepository questionReportRepo;

    @Mock
    private QuestionReportHistoryRepository questionReportHistoryRepo;

    @Mock
    private ExamUploadRequestRepository examUploadRequestRepository;

    @Mock
    private TagRepository tagRepo;

    @Mock
    private TagService tagService;

    @Mock
    private ExamFlowCacheService examFlowCacheService;

    @Mock
    private ExamAuditService examAuditService;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private RabbitMQEventPublisher rabbitMQEventPublisher;

    @InjectMocks
    private ExamManagementService service;

    @Test
    @DisplayName("deleteExam removes SM2 records before deleting questions")
    void deleteExamRemovesSm2RecordsBeforeDeletingQuestions() {
        Long examId = 2L;
        Long questionId = 101L;
        List<Long> questionIds = List.of(questionId);

        OnlineExam exam = new OnlineExam();
        exam.setId(examId);
        exam.setTitle("Đề thi bị xóa");

        Question question = new Question();
        question.setId(questionId);

        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(questionRepo.findByExamIdOrderByIdAsc(examId)).thenReturn(List.of(question));
        when(examAttemptRepo.findIdsByExamId(examId)).thenReturn(List.of());
        when(questionReportRepo.findIdsByQuestionIdIn(questionIds)).thenReturn(List.of());
        when(authenticatedUserService.currentUserHasRole("ADMIN")).thenReturn(true);
        when(authenticatedUserService.getCurrentUserId()).thenReturn(99L);

        service.deleteExam(examId);

        InOrder deleteOrder = inOrder(questionReportRepo, sm2RecordRepo, optionRepo, questionRepo, examRepo);
        deleteOrder.verify(questionReportRepo).deleteByQuestionIdIn(questionIds);
        deleteOrder.verify(sm2RecordRepo).deleteByQuestionIdIn(questionIds);
        deleteOrder.verify(optionRepo).deleteByQuestionIdIn(questionIds);
        deleteOrder.verify(questionRepo).deleteByExamId(examId);
        deleteOrder.verify(examRepo).delete(exam);

        verify(examFlowCacheService).evictExam(examId);
    }

    @Test
    @DisplayName("updateExamStatus rejects publishing when totalQuestions is zero")
    void updateExamStatus_whenPublishingAndTotalQuestionsZero_thenThrowBadRequest() {
        Long examId = 10L;
        OnlineExam exam = baseExam(examId, OnlineExamStatus.DRAFT, 0);
        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(authenticatedUserService.currentUserHasRole("ADMIN")).thenReturn(true);

        assertThatThrownBy(() -> service.updateExamStatus(examId, OnlineExamStatus.PUBLISHED))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(examRepo, never()).save(org.mockito.ArgumentMatchers.any(OnlineExam.class));
    }

    @Test
    @DisplayName("updateExamStatus rejects publishing when totalQuestions is null")
    void updateExamStatus_whenPublishingAndTotalQuestionsNull_thenThrowBadRequest() {
        Long examId = 11L;
        OnlineExam exam = baseExam(examId, OnlineExamStatus.DRAFT, null);
        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(authenticatedUserService.currentUserHasRole("ADMIN")).thenReturn(true);

        assertThatThrownBy(() -> service.updateExamStatus(examId, OnlineExamStatus.PUBLISHED))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verify(examRepo, never()).save(org.mockito.ArgumentMatchers.any(OnlineExam.class));
    }

    @Test
    @DisplayName("updateExamStatus publishes successfully when totalQuestions is positive")
    void updateExamStatus_whenPublishingAndTotalQuestionsPositive_thenSuccess() {
        Long examId = 12L;
        OnlineExam exam = baseExam(examId, OnlineExamStatus.DRAFT, 5);
        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(examRepo.save(org.mockito.ArgumentMatchers.any(OnlineExam.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticatedUserService.currentUserHasRole("ADMIN")).thenReturn(true);
        when(authenticatedUserService.getCurrentUserId()).thenReturn(99L);

        var response = service.updateExamStatus(examId, OnlineExamStatus.PUBLISHED);

        assertThat(response.getStatus()).isEqualTo(OnlineExamStatus.PUBLISHED);
        verify(examRepo).save(org.mockito.ArgumentMatchers.any(OnlineExam.class));
        verify(examFlowCacheService).evictExam(examId);
        verify(examAuditService).log(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(examId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("updateExamStatus allows draft even when totalQuestions is zero")
    void updateExamStatus_whenSettingDraftAndTotalQuestionsZero_thenSuccess() {
        Long examId = 13L;
        OnlineExam exam = baseExam(examId, OnlineExamStatus.ARCHIVED, 0);
        when(examRepo.findById(examId)).thenReturn(Optional.of(exam));
        when(examRepo.save(org.mockito.ArgumentMatchers.any(OnlineExam.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(authenticatedUserService.currentUserHasRole("ADMIN")).thenReturn(true);
        when(authenticatedUserService.getCurrentUserId()).thenReturn(100L);

        var response = service.updateExamStatus(examId, OnlineExamStatus.DRAFT);

        assertThat(response.getStatus()).isEqualTo(OnlineExamStatus.DRAFT);
        verify(examRepo).save(org.mockito.ArgumentMatchers.any(OnlineExam.class));
    }

    @Test
    @DisplayName("getManagedExams returns only contributor-owned exams for contributor")
    void getManagedExams_whenContributor_thenReturnOwnedExamsOnly() {
        OnlineExam ownExam = baseExam(20L, OnlineExamStatus.DRAFT, 3);
        ownExam.setCreatedBy("55");

        when(authenticatedUserService.currentUserHasRole("ADMIN")).thenReturn(false);
        when(authenticatedUserService.getCurrentUserId()).thenReturn(55L);
        when(examRepo.findByCreatedByOrderByCreatedAtDesc("55")).thenReturn(List.of(ownExam));

        var result = service.getManagedExams();

        assertThat(result).hasSize(1);
        verify(examRepo).findByCreatedByOrderByCreatedAtDesc("55");
        verify(examRepo, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    @DisplayName("getManagedExams hides AI extracted drafts until extraction completes")
    void getManagedExams_hidesAiExtractedDraftPlaceholder() {
        OnlineExam readyExam = baseExam(30L, OnlineExamStatus.DRAFT, 4);
        readyExam.setCreatedBy("55");

        OnlineExam processingExam = baseExam(31L, OnlineExamStatus.DRAFT, 0);
        processingExam.setCreatedBy("55");
        processingExam.setSource(OnlineExamSource.AI_EXTRACTED);

        when(authenticatedUserService.currentUserHasRole("ADMIN")).thenReturn(false);
        when(authenticatedUserService.getCurrentUserId()).thenReturn(55L);
        when(examRepo.findByCreatedByOrderByCreatedAtDesc("55")).thenReturn(List.of(processingExam, readyExam));
        when(examUploadRequestRepository.findHiddenManagedExamIds(Set.of(31L), List.of(ExamUploadStatus.EXTRACTED)))
                .thenReturn(Set.of(31L));

        var result = service.getManagedExams();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(30L);
    }

    @Test
    @DisplayName("getManagedExamById rejects contributor when exam belongs to another creator")
    void getManagedExamById_whenContributorAccessesOtherExam_thenThrowNotFound() {
        OnlineExam exam = baseExam(21L, OnlineExamStatus.DRAFT, 2);
        exam.setCreatedBy("77");

        when(authenticatedUserService.currentUserHasRole("ADMIN")).thenReturn(false);
        when(authenticatedUserService.getCurrentUserId()).thenReturn(55L);
        when(examRepo.findById(21L)).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> service.getManagedExamById(21L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("getManagedExamById hides AI extracted draft while extraction is still running")
    void getManagedExamById_whenAiExtractionStillRunning_thenThrowNotFound() {
        OnlineExam exam = baseExam(22L, OnlineExamStatus.DRAFT, 0);
        exam.setCreatedBy("55");
        exam.setSource(OnlineExamSource.AI_EXTRACTED);

        ExamUploadRequest upload = new ExamUploadRequest();
        upload.setId(901L);
        upload.setStatus(ExamUploadStatus.EXTRACTING);
        upload.setExtractedExamId(22L);

        when(authenticatedUserService.currentUserHasRole("ADMIN")).thenReturn(false);
        when(authenticatedUserService.getCurrentUserId()).thenReturn(55L);
        when(examRepo.findById(22L)).thenReturn(Optional.of(exam));
        when(examUploadRequestRepository.findByExtractedExamId(22L)).thenReturn(Optional.of(upload));

        assertThatThrownBy(() -> service.getManagedExamById(22L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private OnlineExam baseExam(Long id, OnlineExamStatus status, Integer totalQuestions) {
        OnlineExam exam = new OnlineExam();
        exam.setId(id);
        exam.setTitle("Exam " + id);
        exam.setStatus(status);
        exam.setTotalQuestions(totalQuestions);
        exam.setSource(OnlineExamSource.MANUAL_CREATED);
        return exam;
    }
}
