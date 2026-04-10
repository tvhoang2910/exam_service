package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.entity.Question;
import com.exam_bank.exam_service.entity.Sm2Record;
import com.exam_bank.exam_service.repository.QuestionRepository;
import com.exam_bank.exam_service.repository.Sm2RecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Sm2Service Unit Tests")
class Sm2ServiceTest {

    @Mock
    private Sm2RecordRepository sm2RecordRepository;

    @Mock
    private QuestionRepository questionRepository;

    @InjectMocks
    private Sm2Service sm2Service;

    @Test
    @DisplayName("recordAttempt creates first successful repetition with interval=1 day")
    void recordAttemptCreatesFirstSuccessfulRepetition() {
        Long userId = 7L;
        Long questionId = 99L;

        Question question = new Question();
        question.setId(questionId);

        when(sm2RecordRepository.findByUserIdAndQuestionId(userId, questionId)).thenReturn(Optional.empty());
        when(questionRepository.getReferenceById(questionId)).thenReturn(question);

        Instant before = Instant.now();
        sm2Service.recordAttempt(userId, questionId, 5);
        Instant after = Instant.now();

        ArgumentCaptor<Sm2Record> captor = ArgumentCaptor.forClass(Sm2Record.class);
        verify(sm2RecordRepository).save(captor.capture());
        Sm2Record saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getQuestion()).isSameAs(question);
        assertThat(saved.getLastQuality()).isEqualTo(5);
        assertThat(saved.getRepetition()).isEqualTo(1);
        assertThat(saved.getIntervalDays()).isEqualTo(1);
        assertThat(saved.getEasinessFactor()).isEqualTo(2.6);
        assertThat(saved.getNextReviewAt())
                .isAfterOrEqualTo(before.plus(1, ChronoUnit.DAYS))
                .isBeforeOrEqualTo(after.plus(1, ChronoUnit.DAYS).plusSeconds(1));
    }

    @Test
    @DisplayName("recordAttempt promotes second successful repetition to interval=6 days")
    void recordAttemptPromotesSecondSuccessfulRepetition() {
        Sm2Record existing = new Sm2Record();
        existing.setUserId(2L);
        existing.setRepetition(1);
        existing.setIntervalDays(1);
        existing.setEasinessFactor(2.5);

        when(sm2RecordRepository.findByUserIdAndQuestionId(2L, 8L)).thenReturn(Optional.of(existing));

        sm2Service.recordAttempt(2L, 8L, 4);

        assertThat(existing.getRepetition()).isEqualTo(2);
        assertThat(existing.getIntervalDays()).isEqualTo(6);
        assertThat(existing.getLastQuality()).isEqualTo(4);
    }

    @Test
    @DisplayName("recordAttempt uses rounded EF multiplication from repetition 3 onward")
    void recordAttemptUsesRoundedEfMultiplication() {
        Sm2Record existing = new Sm2Record();
        existing.setUserId(5L);
        existing.setRepetition(2);
        existing.setIntervalDays(6);
        existing.setEasinessFactor(2.5);

        when(sm2RecordRepository.findByUserIdAndQuestionId(5L, 10L)).thenReturn(Optional.of(existing));

        sm2Service.recordAttempt(5L, 10L, 5);

        assertThat(existing.getRepetition()).isEqualTo(3);
        assertThat(existing.getEasinessFactor()).isEqualTo(2.6);
        assertThat(existing.getIntervalDays()).isEqualTo(16);
    }

    @Test
    @DisplayName("recordAttempt floors EF at 1.3 and resets failure attempts")
    void recordAttemptFloorsEfAndResetsOnFailure() {
        Sm2Record existing = new Sm2Record();
        existing.setUserId(3L);
        existing.setRepetition(4);
        existing.setIntervalDays(20);
        existing.setEasinessFactor(1.31);

        when(sm2RecordRepository.findByUserIdAndQuestionId(3L, 11L)).thenReturn(Optional.of(existing));

        sm2Service.recordAttempt(3L, 11L, 0);

        assertThat(existing.getEasinessFactor()).isEqualTo(1.3);
        assertThat(existing.getRepetition()).isEqualTo(0);
        assertThat(existing.getIntervalDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("getDueReviews delegates to repository with current timestamp")
    void getDueReviewsDelegatesToRepository() {
        Sm2Record r1 = new Sm2Record();
        Sm2Record r2 = new Sm2Record();
        when(sm2RecordRepository.findByUserIdAndNextReviewAtBefore(eq(15L), any(Instant.class)))
                .thenReturn(List.of(r1, r2));

        List<Sm2Record> dueReviews = sm2Service.getDueReviews(15L);

        assertThat(dueReviews).containsExactly(r1, r2);
        verify(sm2RecordRepository).findByUserIdAndNextReviewAtBefore(eq(15L), any(Instant.class));
    }
}
