package com.exam_bank.exam_service.integration;

import com.exam_bank.exam_service.dto.message.AiExtractionResultEvent;
import com.exam_bank.exam_service.entity.OnlineExam;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadRequest;
import com.exam_bank.exam_service.feature.upload.entity.ExamUploadStatus;
import com.exam_bank.exam_service.feature.upload.repository.ExamUploadRequestRepository;
import com.exam_bank.exam_service.listener.ExtractionResultListener;
import com.exam_bank.exam_service.repository.OnlineExamRepository;
import com.exam_bank.exam_service.repository.QuestionOptionRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import com.exam_bank.exam_service.service.ExamSseService;
import com.exam_bank.exam_service.service.ExtractionResultService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = UploadExtractFlowIntegrationTest.TestConfig.class)
@DisplayName("Upload extraction flow integration smoke")
class UploadExtractFlowIntegrationTest {

    @SpringBootConfiguration
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        ExtractionResultService extractionResultService(
                ExamUploadRequestRepository uploadRequestRepository,
                OnlineExamRepository examRepository,
                QuestionRepository questionRepository,
                QuestionOptionRepository questionOptionRepository,
                ExamSseService examSseService,
                ObjectMapper objectMapper) {
            return new ExtractionResultService(
                    uploadRequestRepository,
                    examRepository,
                    questionRepository,
                    questionOptionRepository,
                    examSseService,
                    objectMapper);
        }

        @Bean
        ExtractionResultListener extractionResultListener(ExtractionResultService extractionResultService) {
            return new ExtractionResultListener(extractionResultService);
        }
    }

    @Autowired
    private ExtractionResultListener extractionResultListener;

    @MockitoBean
    private ExamUploadRequestRepository uploadRequestRepository;

    @MockitoBean
    private OnlineExamRepository examRepository;

    @MockitoBean
    private QuestionRepository questionRepository;

    @MockitoBean
    private QuestionOptionRepository questionOptionRepository;

    @MockitoBean
    private ExamSseService examSseService;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    void handle_whenSuccessfulExtractionEvent_thenUpdateUploadAndPersistQuestions() {
        ExamUploadRequest upload = new ExamUploadRequest();
        upload.setId(700L);
        upload.setUploaderId(42L);
        upload.setStatus(ExamUploadStatus.EXTRACTING);

        OnlineExam exam = new OnlineExam();
        exam.setId(800L);
        exam.setTitle("Demo exam");
        exam.setTotalQuestions(0);

        when(uploadRequestRepository.findById(700L)).thenReturn(Optional.of(upload));
        when(examRepository.findById(800L)).thenReturn(Optional.of(exam));
        when(uploadRequestRepository.save(any(ExamUploadRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(examRepository.save(any(OnlineExam.class))).thenAnswer(inv -> inv.getArgument(0));

        AtomicLong nextQuestionId = new AtomicLong(1L);
        when(questionRepository.save(any())).thenAnswer(inv -> {
            var q = inv.getArgument(0, com.exam_bank.exam_service.entity.Question.class);
            q.setId(nextQuestionId.getAndIncrement());
            return q;
        });
        when(questionOptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AiExtractionResultEvent event = AiExtractionResultEvent.builder()
                .uploadRequestId(700L)
                .examId(800L)
                .uploadedByUserId("42")
                .successFlag(true)
                .aiJsonResult(
                        """
                                [
                                  {"content":"Q1","explanation":"E1","difficulty":"EASY","options":[{"content":"A1","isCorrect":true},{"content":"A2","isCorrect":false}]},
                                  {"content":"Q2","explanation":"E2","difficulty":"MEDIUM","options":[{"content":"B1","isCorrect":true},{"content":"B2","isCorrect":false}]}
                                ]
                                """)
                .errorMessage(null)
                .timestamp(System.currentTimeMillis())
                .build();

        extractionResultListener.handle(event);

        assertThat(upload.getStatus()).isEqualTo(ExamUploadStatus.EXTRACTED);
        assertThat(upload.getExtractedExamId()).isEqualTo(800L);
        assertThat(exam.getTotalQuestions()).isEqualTo(2);

        verify(questionRepository, times(2)).save(any());
        verify(questionOptionRepository, times(4)).save(any());
        verify(examSseService).sendToUser(eq(42L), eq("exam"), any());
    }
}
