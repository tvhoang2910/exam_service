package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.message.AiExtractionResultEvent;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.entity.QuestionOption;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadRequest;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import com.exam_bank.exam_service.feature.upload.repository.ExamUploadRequestRepository;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.repository.QuestionOptionRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExtractionResultService unit tests")
class ExtractionResultServiceTest {

    @Mock
    private ExamUploadRequestRepository uploadRequestRepository;

    @Mock
    private OnlineExamRepository examRepository;

    @Mock
    private QuestionRepository questionRepository;

    @Mock
    private QuestionOptionRepository questionOptionRepository;

    @Mock
    private ExamSseService examSseService;

    private ExtractionResultService service;

    @BeforeEach
    void setUp() {
        service = new ExtractionResultService(
                uploadRequestRepository,
                examRepository,
                questionRepository,
                questionOptionRepository,
                examSseService,
                new ObjectMapper());
    }

    @Test
    void processExtractionResult_whenSuccessJsonHasThreeQuestions_thenPersistAndMarkExtracted() {
        ExamUploadRequest upload = upload(10L, 77L, ExamUploadStatus.EXTRACTING);
        OnlineExam exam = exam(999L);

        when(uploadRequestRepository.findById(10L)).thenReturn(Optional.of(upload));
        when(examRepository.findById(999L)).thenReturn(Optional.of(exam));
        when(uploadRequestRepository.save(any(ExamUploadRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(examRepository.save(any(OnlineExam.class))).thenAnswer(inv -> inv.getArgument(0));

        AtomicLong questionId = new AtomicLong(1L);
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> {
            Question saved = inv.getArgument(0);
            saved.setId(questionId.getAndIncrement());
            return saved;
        });
        when(questionOptionRepository.save(any(QuestionOption.class))).thenAnswer(inv -> inv.getArgument(0));

        AiExtractionResultEvent event = AiExtractionResultEvent.builder()
                .uploadRequestId(10L)
                .examId(999L)
                .uploadedByUserId("77")
                .successFlag(true)
                .aiJsonResult(
                        """
                                [
                                  {"content":"Q1","explanation":"E1","difficulty":"EASY","options":[{"content":"A1","isCorrect":true},{"content":"A2","isCorrect":false}]},
                                  {"content":"Q2","explanation":"E2","difficulty":"MEDIUM","options":[{"content":"B1","isCorrect":true},{"content":"B2","isCorrect":false}]},
                                  {"content":"Q3","explanation":"E3","difficulty":"HARD","options":[{"content":"C1","isCorrect":true},{"content":"C2","isCorrect":false}]}
                                ]
                                """)
                .errorMessage(null)
                .timestamp(System.currentTimeMillis())
                .build();

        service.processExtractionResult(event);

        assertThat(upload.getStatus()).isEqualTo(ExamUploadStatus.EXTRACTED);
        assertThat(upload.getExtractedExamId()).isEqualTo(999L);
        assertThat(upload.getExtractionError()).isNull();
        assertThat(exam.getTotalQuestions()).isEqualTo(3);

        verify(questionRepository, times(3)).save(any(Question.class));
        verify(questionOptionRepository, times(6)).save(any(QuestionOption.class));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(examSseService).sendToUser(eq(77L), eq("exam"), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload.get("eventType")).isEqualTo("AI_EXTRACTION_SUCCESS");
        assertThat(payload.get("uploadRequestId")).isEqualTo(10L);
        assertThat(payload.get("extractedExamId")).isEqualTo(999L);
    }

    @Test
    void processExtractionResult_whenJsonHasMarkdownFence_thenParseSuccessfully() {
        ExamUploadRequest upload = upload(11L, 77L, ExamUploadStatus.EXTRACTING);
        OnlineExam exam = exam(1000L);

        when(uploadRequestRepository.findById(11L)).thenReturn(Optional.of(upload));
        when(examRepository.findById(1000L)).thenReturn(Optional.of(exam));
        when(uploadRequestRepository.save(any(ExamUploadRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(examRepository.save(any(OnlineExam.class))).thenAnswer(inv -> inv.getArgument(0));
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> {
            Question saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(questionOptionRepository.save(any(QuestionOption.class))).thenAnswer(inv -> inv.getArgument(0));

        AiExtractionResultEvent event = AiExtractionResultEvent.builder()
                .uploadRequestId(11L)
                .examId(1000L)
                .successFlag(true)
                .aiJsonResult(
                        """
                                ```json
                                [
                                  {"content":"Q1","explanation":"","difficulty":"MEDIUM","options":[{"content":"A","isCorrect":true}]}
                                ]
                                ```
                                """)
                .build();

        service.processExtractionResult(event);

        assertThat(upload.getStatus()).isEqualTo(ExamUploadStatus.EXTRACTED);
        assertThat(exam.getTotalQuestions()).isEqualTo(1);
        verify(questionRepository).save(any(Question.class));
    }

    @Test
    void processExtractionResult_whenAiReturnsEmptyArray_thenMarkExtractFailed() {
        ExamUploadRequest upload = upload(12L, 88L, ExamUploadStatus.EXTRACTING);
        OnlineExam exam = exam(1001L);

        when(uploadRequestRepository.findById(12L)).thenReturn(Optional.of(upload));
        when(examRepository.findById(1001L)).thenReturn(Optional.of(exam));
        when(uploadRequestRepository.save(any(ExamUploadRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        AiExtractionResultEvent event = AiExtractionResultEvent.builder()
                .uploadRequestId(12L)
                .examId(1001L)
                .successFlag(true)
                .aiJsonResult("[]")
                .build();

        service.processExtractionResult(event);

        assertThat(upload.getStatus()).isEqualTo(ExamUploadStatus.EXTRACT_FAILED);
        assertThat(upload.getExtractionError()).isEqualTo("AI returned 0 questions");
        verify(questionRepository, never()).save(any(Question.class));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(examSseService).sendToUser(eq(88L), eq("exam"), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload.get("eventType")).isEqualTo("AI_EXTRACTION_FAILED");
    }

    @Test
    void processExtractionResult_whenSuccessFlagFalse_thenMarkFailedAndTruncateError() {
        ExamUploadRequest upload = upload(13L, 99L, ExamUploadStatus.EXTRACTING);
        OnlineExam exam = exam(1002L);

        when(uploadRequestRepository.findById(13L)).thenReturn(Optional.of(upload));
        when(examRepository.findById(1002L)).thenReturn(Optional.of(exam));
        when(uploadRequestRepository.save(any(ExamUploadRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        String longError = "x".repeat(2200);
        AiExtractionResultEvent event = AiExtractionResultEvent.builder()
                .uploadRequestId(13L)
                .examId(1002L)
                .successFlag(false)
                .errorMessage(longError)
                .build();

        service.processExtractionResult(event);

        assertThat(upload.getStatus()).isEqualTo(ExamUploadStatus.EXTRACT_FAILED);
        assertThat(upload.getExtractionError())
                .hasSize(2000)
                .isEqualTo(longError.substring(0, 2000));
        verify(questionRepository, never()).save(any(Question.class));
    }

    @Test
    void processExtractionResult_whenUploadAlreadyExtracted_thenSkipIdempotent() {
        ExamUploadRequest upload = upload(14L, 11L, ExamUploadStatus.EXTRACTED);
        when(uploadRequestRepository.findById(14L)).thenReturn(Optional.of(upload));

        AiExtractionResultEvent event = AiExtractionResultEvent.builder()
                .uploadRequestId(14L)
                .examId(1003L)
                .successFlag(true)
                .aiJsonResult("[{\"content\":\"Q\"}]")
                .build();

        service.processExtractionResult(event);

        verify(examRepository, never()).findById(anyLong());
        verify(uploadRequestRepository, never()).save(any(ExamUploadRequest.class));
        verifyNoInteractions(questionRepository, questionOptionRepository, examSseService);
    }

    @Test
    void processExtractionResult_whenUploadRejected_thenSkipIdempotent() {
        ExamUploadRequest upload = upload(15L, 12L, ExamUploadStatus.REJECTED);
        when(uploadRequestRepository.findById(15L)).thenReturn(Optional.of(upload));

        AiExtractionResultEvent event = AiExtractionResultEvent.builder()
                .uploadRequestId(15L)
                .examId(1004L)
                .successFlag(true)
                .aiJsonResult("[{\"content\":\"Q\"}]")
                .build();

        service.processExtractionResult(event);

        verify(examRepository, never()).findById(anyLong());
        verify(uploadRequestRepository, never()).save(any(ExamUploadRequest.class));
        verifyNoInteractions(questionRepository, questionOptionRepository, examSseService);
    }

    @Test
    void processExtractionResult_whenUploadRequestNotFound_thenThrowIllegalState() {
        when(uploadRequestRepository.findById(404L)).thenReturn(Optional.empty());

        AiExtractionResultEvent event = AiExtractionResultEvent.builder()
                .uploadRequestId(404L)
                .examId(1005L)
                .successFlag(true)
                .aiJsonResult("[]")
                .build();

        assertThatThrownBy(() -> service.processExtractionResult(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ExamUploadRequest not found");

        verifyNoInteractions(examRepository, questionRepository, questionOptionRepository, examSseService);
    }

    private ExamUploadRequest upload(Long id, Long uploaderId, ExamUploadStatus status) {
        ExamUploadRequest upload = new ExamUploadRequest();
        upload.setId(id);
        upload.setUploaderId(uploaderId);
        upload.setStatus(status);
        upload.setTitle("Upload " + id);
        upload.setPageCount(2);
        upload.setContentType("application/pdf");
        return upload;
    }

    private OnlineExam exam(Long id) {
        OnlineExam exam = new OnlineExam();
        exam.setId(id);
        exam.setTitle("Exam " + id);
        exam.setTotalQuestions(0);
        return exam;
    }
}
