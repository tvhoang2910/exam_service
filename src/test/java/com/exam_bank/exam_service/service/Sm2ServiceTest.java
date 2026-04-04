package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.entity.Sm2Record;
import com.exam_bank.exam_service.repository.Sm2RecordRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Sm2ServiceTest {

    @Mock
    private Sm2RecordRepository sm2RecordRepository;
    @Mock
    private QuestionRepository questionRepository;

    @InjectMocks
    private Sm2Service sm2Service;

    @Test
    void recordAttempt_newQuestion_quality5_setsCorrectIntervals() {
        // First attempt, quality=5
        // EF' = 2.5 + (0.1 - 0*(0.08+0*0.02)) = 2.5 + 0.1 = 2.6
        // rep < 3 so rep becomes 1, interval = 1
        when(sm2RecordRepository.findByUserIdAndQuestionId(1L, 100L))
                .thenReturn(Optional.empty());
        when(questionRepository.getReferenceById(100L)).thenReturn(new com.exam_bank.exam_service.entity.Question());
        when(sm2RecordRepository.save(any(Sm2Record.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        sm2Service.recordAttempt(1L, 100L, 5);

        ArgumentCaptor<Sm2Record> captor = ArgumentCaptor.forClass(Sm2Record.class);
        verify(sm2RecordRepository).save(captor.capture());
        Sm2Record saved = captor.getValue();
        assertEquals(2.6, saved.getEasinessFactor(), 0.01); // 2.5 + 0.1
        assertEquals(1, saved.getRepetition());
        assertEquals(1, saved.getIntervalDays());
        assertEquals(5, saved.getLastQuality());
    }

    @Test
    void recordAttempt_existingQuestion_quality5_incrementsRepAndInterval() {
        Sm2Record existing = new Sm2Record();
        existing.setRepetition(1);
        existing.setIntervalDays(1);
        existing.setEasinessFactor(2.5);
        when(sm2RecordRepository.findByUserIdAndQuestionId(1L, 100L))
                .thenReturn(Optional.of(existing));
        when(sm2RecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sm2Service.recordAttempt(1L, 100L, 5);

        // EF' = 2.5 + 0.1 = 2.6, rep=2, interval = 6 by SM2 base rule
        assertEquals(2, existing.getRepetition());
        assertEquals(6, existing.getIntervalDays());
    }

    @Test
    void recordAttempt_quality1_resetsRepAndInterval() {
        Sm2Record existing = new Sm2Record();
        existing.setRepetition(5);
        existing.setIntervalDays(30);
        existing.setEasinessFactor(2.5);
        when(sm2RecordRepository.findByUserIdAndQuestionId(1L, 100L))
                .thenReturn(Optional.of(existing));
        when(sm2RecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sm2Service.recordAttempt(1L, 100L, 1);

        assertEquals(0, existing.getRepetition());
        assertEquals(1, existing.getIntervalDays());
    }

    @Test
    void recordAttempt_quality0_resetsRepAndInterval() {
        Sm2Record existing = new Sm2Record();
        existing.setRepetition(3);
        existing.setIntervalDays(10);
        existing.setEasinessFactor(2.0);
        when(sm2RecordRepository.findByUserIdAndQuestionId(1L, 100L))
                .thenReturn(Optional.of(existing));
        when(sm2RecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sm2Service.recordAttempt(1L, 100L, 0);

        assertEquals(0, existing.getRepetition());
        assertEquals(1, existing.getIntervalDays());
    }

    @Test
    void recordAttempt_easinessFactorNeverBelow1_3() {
        Sm2Record existing = new Sm2Record();
        existing.setEasinessFactor(1.4);
        existing.setRepetition(1);
        existing.setIntervalDays(1);
        when(sm2RecordRepository.findByUserIdAndQuestionId(1L, 100L))
                .thenReturn(Optional.of(existing));
        when(sm2RecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // quality=0: ef = 1.4 + (0.1 - 5*(0.08 + 5*0.02)) = 1.4 + (0.1 - 0.58) = 0.92 →
        // floor to 1.3
        sm2Service.recordAttempt(1L, 100L, 0);

        assertEquals(1.3, existing.getEasinessFactor(), 0.01);
    }
}
