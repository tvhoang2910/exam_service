package com.exam_bank.exam_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.exam_bank.exam_service.dto.ExamResponse;
import com.exam_bank.exam_service.dto.StartAttemptRequest;
import com.exam_bank.exam_service.dto.StartAttemptResponse;
import com.exam_bank.exam_service.entity.ExamAttempt;
import com.exam_bank.exam_service.entity.ExamAttemptStatus;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.OnlineExamStatus;
import com.exam_bank.exam_service.repository.ExamAttemptAnswerRepository;
import com.exam_bank.exam_service.repository.ExamAttemptRepository;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.repository.QuestionOptionRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import com.exam_bank.exam_service.repository.QuestionReviewEventRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExamAttemptService Premium Access Tests")
class ExamAttemptServiceTest {

    @Mock
    private OnlineExamRepository examRepository;

    @Mock
    private ExamAttemptRepository examAttemptRepository;

    @Mock
    private ExamAttemptAnswerRepository examAttemptAnswerRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionOptionRepository questionOptionRepository;

    @Mock
    private QuestionReviewEventRepository questionReviewEventRepository;

    @Mock
    private Sm2Service sm2Service;

    @Mock
    private ExamManagementService examManagementService;

    @Mock
    private ExamFlowCacheService examFlowCacheService;

    @Mock
    private RabbitMQEventPublisher rabbitMQEventPublisher;

    @Mock
    private AdminAlertPublisher adminAlertPublisher;

    @Mock
    private ExamSseService examSseService;

    @Mock
    private AuthUserLookupClient authUserLookupClient;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @InjectMocks
    private ExamAttemptService examAttemptService;

    @Test
    @DisplayName("getAttemptView returns teaser view when exam is premium and user is not premium")
    void getAttemptViewReturnsTeaserWhenPremiumLocked() {
        OnlineExam exam = buildPublishedExam(true, 2);
        ExamResponse expected = new ExamResponse();
        expected.setId(exam.getId());
        expected.setPremiumLocked(true);

        when(examRepository.findById(99L)).thenReturn(Optional.of(exam));
        when(authenticatedUserService.getCurrentUserIdOptional()).thenReturn(Optional.of(7L));
        when(authUserLookupClient.findPremiumStatusByUserId(7L)).thenReturn(Optional.of(false));
        when(examManagementService.mapPublicAttemptView(exam, 2, true)).thenReturn(expected);

        ExamResponse result = examAttemptService.getAttemptView(99L);

        assertThat(result).isSameAs(expected);
        verify(examManagementService).mapPublicAttemptView(exam, 2, true);
    }

    @Test
    @DisplayName("getAttemptView returns full view for premium user")
    void getAttemptViewReturnsFullViewForPremiumUser() {
        OnlineExam exam = buildPublishedExam(true, 2);
        ExamResponse expected = new ExamResponse();
        expected.setId(exam.getId());
        expected.setPremiumLocked(false);

        when(examRepository.findById(99L)).thenReturn(Optional.of(exam));
        when(authenticatedUserService.getCurrentUserIdOptional()).thenReturn(Optional.of(7L));
        when(authUserLookupClient.findPremiumStatusByUserId(7L)).thenReturn(Optional.of(true));
        when(examManagementService.mapPublicAttemptView(exam, null, false)).thenReturn(expected);

        ExamResponse result = examAttemptService.getAttemptView(99L);

        assertThat(result).isSameAs(expected);
        verify(examManagementService).mapPublicAttemptView(exam, null, false);
    }

    @Test
    @DisplayName("startAttempt rejects non-premium user on premium exam")
    void startAttemptRejectsNonPremiumUserForPremiumExam() {
        OnlineExam exam = buildPublishedExam(true, 2);
        StartAttemptRequest request = new StartAttemptRequest();
        request.setExamId(99L);

        when(examRepository.findById(99L)).thenReturn(Optional.of(exam));
        when(authUserLookupClient.findPremiumStatusByUserId(10L)).thenReturn(Optional.of(false));

        assertThatThrownBy(() -> examAttemptService.startAttempt(request, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) exception;
                    assertThat(responseStatusException.getStatusCode().value()).isEqualTo(403);
                    assertThat(responseStatusException.getReason())
                            .contains("Premium exam requires an active Premium subscription");
                });
    }

    @Test
    @DisplayName("startAttempt fails fast when published exam has zero questions")
    void startAttempt_whenExamHasZeroQuestions_thenBadRequestAndNoSave() {
        OnlineExam exam = buildPublishedExam(true, 2);
        exam.setTotalQuestions(0);

        StartAttemptRequest request = new StartAttemptRequest();
        request.setExamId(99L);

        when(examRepository.findById(99L)).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> examAttemptService.startAttempt(request, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) exception;
                    assertThat(responseStatusException.getStatusCode().value()).isEqualTo(400);
                    assertThat(responseStatusException.getReason()).contains("Exam has no questions");
                });

        verify(examAttemptRepository, never()).save(any(ExamAttempt.class));
        verify(authUserLookupClient, never()).findPremiumStatusByUserId(anyLong());
    }

    @Test
    @DisplayName("startAttempt creates attempt when exam has questions and is available")
    void startAttempt_whenExamHasQuestions_thenCreateAttemptSuccessfully() {
        OnlineExam exam = buildPublishedExam(false, 2);
        exam.setTotalQuestions(5);

        StartAttemptRequest request = new StartAttemptRequest();
        request.setExamId(99L);
        request.setClientVersion("web-1.0.0");

        when(examRepository.findById(99L)).thenReturn(Optional.of(exam));
        when(examAttemptRepository
                .findFirstByExamIdAndUserIdAndStatusOrderByCreatedAtDesc(99L, 10L, ExamAttemptStatus.IN_PROGRESS))
                .thenReturn(Optional.empty());
        when(examAttemptRepository.countByExamIdAndUserIdAndStatusIn(eq(99L), eq(10L), anyCollection()))
                .thenReturn(0L);
        when(examAttemptRepository.save(any(ExamAttempt.class))).thenAnswer(invocation -> {
            ExamAttempt saved = invocation.getArgument(0);
            saved.setId(500L);
            return saved;
        });

        StartAttemptResponse response = examAttemptService.startAttempt(request, 10L);

        assertThat(response.getAttemptId()).isEqualTo(500L);
        assertThat(response.getExamId()).isEqualTo(99L);
        assertThat(response.getDurationMinutes()).isEqualTo(60);
        verify(examAttemptRepository).save(any(ExamAttempt.class));
        verify(examSseService).onAttemptStarted(500L, 99L);
    }

    @Test
    @DisplayName("startAttempt checks published status before zero-question guard")
    void startAttempt_whenExamIsNotPublished_thenRejectBeforeZeroQuestionGuard() {
        OnlineExam exam = buildPublishedExam(false, 2);
        exam.setStatus(OnlineExamStatus.DRAFT);
        exam.setTotalQuestions(0);

        StartAttemptRequest request = new StartAttemptRequest();
        request.setExamId(99L);

        when(examRepository.findById(99L)).thenReturn(Optional.of(exam));

        assertThatThrownBy(() -> examAttemptService.startAttempt(request, 10L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseStatusException = (ResponseStatusException) exception;
                    assertThat(responseStatusException.getStatusCode().value()).isEqualTo(400);
                    assertThat(responseStatusException.getReason()).contains("Exam is not available for attempts");
                });

        verify(examAttemptRepository, never()).save(any(ExamAttempt.class));
        verify(authUserLookupClient, never()).findPremiumStatusByUserId(anyLong());
    }

    private OnlineExam buildPublishedExam(boolean premium, int teaserQuestionCount) {
        OnlineExam exam = new OnlineExam();
        exam.setId(99L);
        exam.setTitle("Premium Demo");
        exam.setDescription("Premium demo description");
        exam.setStatus(OnlineExamStatus.PUBLISHED);
        exam.setIsPremium(premium);
        exam.setTeaserQuestionCount(teaserQuestionCount);
        exam.setDurationMinutes(60);
        exam.setMaxAttempts(5);
        exam.setPassingScore(5);
        exam.setTotalQuestions(10);
        exam.setCreatedAt(Instant.now());
        exam.setModifiedAt(Instant.now());
        return exam;
    }
}
