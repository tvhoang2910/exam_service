package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.dto.internal.ExamSseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ExamSseServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private RedisMessageListenerContainer redisContainer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ExamSseService examSseService;

    @BeforeEach
    void setUp() {
        examSseService = new ExamSseService(redisTemplate, redisContainer, objectMapper);
        examSseService.init();
    }

    @Nested
    @DisplayName("onExamSubmitted")
    class OnExamSubmitted {

        @Test
        @DisplayName("should increment submissionsToday counter and publish event")
        void shouldIncrementCounterAndPublish() throws Exception {
            when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

            examSseService.onExamSubmitted(10L, 1L, "Final Exam", 100L);

            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate).convertAndSend(anyString(), jsonCaptor.capture());

            ExamSseEvent sent = objectMapper.readValue(jsonCaptor.getValue(), ExamSseEvent.class);
            assertThat(sent.getEventType()).isEqualTo(ExamSseEvent.EventType.EXAM_SUBMITTED);
            assertThat(sent.getAttemptId()).isEqualTo(10L);
            assertThat(sent.getExamId()).isEqualTo(1L);
            assertThat(sent.getExamTitle()).isEqualTo("Final Exam");
            assertThat(sent.getUserId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("should reflect updated submission count in published event")
        void shouldReflectUpdatedSubmissionCount() throws Exception {
            when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

            examSseService.onExamSubmitted(1L, 1L, "Exam A", 10L);
            examSseService.onExamSubmitted(2L, 2L, "Exam B", 20L);

            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate, times(2)).convertAndSend(anyString(), jsonCaptor.capture());

            ExamSseEvent lastEvent = objectMapper.readValue(jsonCaptor.getAllValues().get(1), ExamSseEvent.class);
            assertThat(lastEvent.getTotalSubmissionsToday()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("onAttemptStarted")
    class OnAttemptStarted {

        @Test
        @DisplayName("should increment activeAttempts counter and publish event")
        void shouldIncrementActiveAttemptsAndPublish() throws Exception {
            when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

            examSseService.onAttemptStarted(20L, 5L);

            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate).convertAndSend(anyString(), jsonCaptor.capture());

            ExamSseEvent sent = objectMapper.readValue(jsonCaptor.getValue(), ExamSseEvent.class);
            assertThat(sent.getEventType()).isEqualTo(ExamSseEvent.EventType.ATTEMPT_STARTED);
            assertThat(sent.getAttemptId()).isEqualTo(20L);
            assertThat(sent.getExamId()).isEqualTo(5L);
            assertThat(sent.getActiveAttemptCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should reflect cumulative active attempt count")
        void shouldReflectCumulativeCount() throws Exception {
            when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

            examSseService.onAttemptStarted(1L, 1L);
            examSseService.onAttemptStarted(2L, 1L);

            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate, times(2)).convertAndSend(anyString(), jsonCaptor.capture());

            ExamSseEvent lastEvent = objectMapper.readValue(jsonCaptor.getAllValues().get(1), ExamSseEvent.class);
            assertThat(lastEvent.getActiveAttemptCount()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("onAttemptEnded")
    class OnAttemptEnded {

        @Test
        @DisplayName("should decrement activeAttempts counter and publish event")
        void shouldDecrementActiveAttemptsAndPublish() throws Exception {
            when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);
            examSseService.onAttemptStarted(30L, 5L);

            examSseService.onAttemptEnded(30L);

            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate, times(2)).convertAndSend(anyString(), jsonCaptor.capture());

            ExamSseEvent sent = objectMapper.readValue(jsonCaptor.getAllValues().get(1), ExamSseEvent.class);
            assertThat(sent.getEventType()).isEqualTo(ExamSseEvent.EventType.ATTEMPT_ENDED);
            assertThat(sent.getAttemptId()).isEqualTo(30L);
            assertThat(sent.getActiveAttemptCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should not go below zero when ending more attempts than started")
        void shouldNotGoBelowZero() throws Exception {
            when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

            examSseService.onAttemptEnded(99L);

            ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(redisTemplate).convertAndSend(anyString(), jsonCaptor.capture());

            ExamSseEvent sent = objectMapper.readValue(jsonCaptor.getValue(), ExamSseEvent.class);
            assertThat(sent.getActiveAttemptCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getActiveAttemptCount")
    class GetActiveAttemptCount {

        @Test
        @DisplayName("should return current active attempt count")
        void shouldReturnActiveAttemptCount() {
            examSseService.onAttemptStarted(1L, 1L);
            examSseService.onAttemptStarted(2L, 2L);

            assertThat(examSseService.getActiveAttemptCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return zero after all attempts ended")
        void shouldReturnZeroAfterAllEnded() {
            examSseService.onAttemptStarted(1L, 1L);
            examSseService.onAttemptEnded(1L);

            assertThat(examSseService.getActiveAttemptCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getSubmissionsTodayCount")
    class GetSubmissionsTodayCount {

        @Test
        @DisplayName("should return cumulative submissions today")
        void shouldReturnCumulativeSubmissions() {
            when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);

            examSseService.onExamSubmitted(1L, 1L, "E1", 10L);
            examSseService.onExamSubmitted(2L, 2L, "E2", 20L);
            examSseService.onExamSubmitted(3L, 3L, "E3", 30L);

            assertThat(examSseService.getSubmissionsTodayCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("resetDailyCounters")
    class ResetDailyCounters {

        @Test
        @DisplayName("should reset submissionsToday to zero")
        void shouldResetSubmissionsToday() {
            when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);
            examSseService.onExamSubmitted(1L, 1L, "E1", 10L);
            examSseService.onExamSubmitted(2L, 2L, "E2", 20L);
            assertThat(examSseService.getSubmissionsTodayCount()).isEqualTo(2);

            examSseService.resetDailyCounters();

            assertThat(examSseService.getSubmissionsTodayCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("should not affect activeAttempts when resetting daily counters")
        void shouldNotAffectActiveAttempts() {
            when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(1L);
            examSseService.onAttemptStarted(1L, 1L);
            examSseService.resetDailyCounters();

            assertThat(examSseService.getActiveAttemptCount()).isEqualTo(1);
        }
    }
}
