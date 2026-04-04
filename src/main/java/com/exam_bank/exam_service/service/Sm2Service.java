package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.entity.Sm2Record;
import com.exam_bank.exam_service.repository.Sm2RecordRepository;
import com.exam_bank.exam_service.repository.QuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class Sm2Service {

    private final Sm2RecordRepository sm2RecordRepository;
    private final QuestionRepository questionRepository;

    /**
     * SM2 Algorithm — record a user's attempt on a question and update the spaced-repetition schedule.
     * @param quality 0–5 from QuestionReviewEvent quality field
     */
    @Transactional
    public void recordAttempt(Long userId, Long questionId, int quality) {
        Sm2Record record = sm2RecordRepository.findByUserIdAndQuestionId(userId, questionId)
                .orElseGet(() -> createNew(userId, questionId));

        record.setLastQuality(quality);

        // Update easiness factor: EF' = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02))
        double ef = record.getEasinessFactor()
                + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02));
        ef = Math.max(1.3, ef); // floor at 1.3
        record.setEasinessFactor(ef);

        if (quality < 3) {
            // Reset on failure
            record.setRepetition(0);
            record.setIntervalDays(1);
        } else {
            int newRep = record.getRepetition() + 1;
            record.setRepetition(newRep);

            int newInterval = switch (newRep) {
                case 1 -> 1;
                case 2 -> 6;
                default -> (int) Math.round(record.getIntervalDays() * ef);
            };
            record.setIntervalDays(newInterval);
        }

        record.setNextReviewAt(Instant.now().plus(record.getIntervalDays(), ChronoUnit.DAYS));
        sm2RecordRepository.save(record);

        log.debug("SM2 record: userId={}, questionId={}, quality={}, ef={}, rep={}, interval={}d, nextReview={}",
                userId, questionId, quality,
                String.format("%.2f", record.getEasinessFactor()),
                record.getRepetition(),
                record.getIntervalDays(),
                record.getNextReviewAt());
    }

    private Sm2Record createNew(Long userId, Long questionId) {
        Sm2Record record = new Sm2Record();
        record.setUserId(userId);
        record.setQuestion(questionRepository.getReferenceById(questionId));
        record.setEasinessFactor(2.5);
        record.setRepetition(0);
        record.setIntervalDays(0);
        return record;
    }

    @Transactional(readOnly = true)
    public List<Sm2Record> getDueReviews(Long userId) {
        return sm2RecordRepository.findByUserIdAndNextReviewAtBefore(userId, Instant.now());
    }
}
