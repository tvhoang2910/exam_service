package com.exam_bank.exam_service.service;

import com.exam_bank.exam_service.repository.QuestionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DifficultyRecalculationService {

    private final QuestionRepository questionRepository;
    private final EntityManager entityManager;

    private static final int MIN_ATTEMPTS = 10;

    /**
     * Scheduled: runs every 6 hours.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void scheduledRecalculate() {
        log.info("Starting scheduled difficulty recalculation");
        int updated = recalculateAll();
        log.info("Scheduled difficulty recalculation done: {} questions updated", updated);
    }

    /**
     * Recalculates difficulty for ALL questions that have >= MIN_ATTEMPTS answer records.
     * Uses a single native UPDATE query for efficiency.
     * @return number of questions updated
     */
    @Transactional
    public int recalculateAll() {
        String sql = """
            UPDATE questions q
            SET difficulty = CASE
                WHEN r.correct_rate >= 80 THEN 'EASY'
                WHEN r.correct_rate >= 50 THEN 'MEDIUM'
                WHEN r.correct_rate >= 20 THEN 'HARD'
                ELSE 'VERY_HARD'
              END,
                updated_at = NOW()
            FROM (
                SELECT item_id,
                       ROUND(SUM(CASE WHEN is_correct THEN 1 ELSE 0 END)::numeric
                             / NULLIF(COUNT(*), 0) * 100, 2) AS correct_rate
                FROM question_review_events
                GROUP BY item_id
                HAVING COUNT(*) >= :minAttempts
            ) r
            WHERE q.id = r.item_id
              AND (q.difficulty IS NULL
                   OR q.difficulty != CASE
                       WHEN r.correct_rate >= 80 THEN 'EASY'
                       WHEN r.correct_rate >= 50 THEN 'MEDIUM'
                       WHEN r.correct_rate >= 20 THEN 'HARD'
                       ELSE 'VERY_HARD'
                   END)
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("minAttempts", MIN_ATTEMPTS);
        int updated = query.executeUpdate();
        log.info("Difficulty recalculation: {} questions updated", updated);
        return updated;
    }

    /**
     * Recalculate difficulty for a single question.
     * Call this after a threshold of new submissions.
     */
    @Transactional
    public void recalculateSingle(Long questionId) {
        String sql = """
            UPDATE questions q
            SET difficulty = CASE
                WHEN r.correct_rate >= 80 THEN 'EASY'
                WHEN r.correct_rate >= 50 THEN 'MEDIUM'
                WHEN r.correct_rate >= 20 THEN 'HARD'
                ELSE 'VERY_HARD'
              END,
                updated_at = NOW()
            FROM (
                SELECT item_id,
                       ROUND(SUM(CASE WHEN is_correct THEN 1 ELSE 0 END)::numeric
                             / NULLIF(COUNT(*), 0) * 100, 2) AS correct_rate
                FROM question_review_events
                WHERE item_id = :questionId
                GROUP BY item_id
                HAVING COUNT(*) >= :minAttempts
            ) r
            WHERE q.id = r.item_id
            """;

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("questionId", questionId);
        query.setParameter("minAttempts", MIN_ATTEMPTS);
        int updated = query.executeUpdate();
        if (updated > 0) {
            log.info("Difficulty recalculated for questionId={}", questionId);
        }
    }
}
