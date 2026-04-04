package com.exam_bank.exam_service.feature.analytics.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.exam_bank.exam_service.entity.QuestionReviewEvent;

@Repository
public interface ContentAnalyticsRepository extends JpaRepository<QuestionReviewEvent, Long> {

    @Query(value = """
            SELECT
                qre.item_id                       AS questionId,
                COUNT(*)                          AS totalAttempts,
                ROUND(SUM(CASE WHEN qre.is_correct = true THEN 1 ELSE 0 END)::numeric
                      / NULLIF(COUNT(*), 0) * 100, 2) AS correctRate,
                ROUND(AVG(qre.latency_ms), 0)    AS avgResponseTimeMs
            FROM question_review_events qre
            WHERE qre.item_id = :questionId
            GROUP BY qre.item_id
            """, nativeQuery = true)
    QuestionStatProjection findQuestionStats(@Param("questionId") Long questionId);

    @Query(value = """
            WITH exam_stats AS (
                SELECT
                    ea.id AS attempt_id,
                    ea.exam_id,
                    ea.score_percent,
                    ea.user_id,
                    NTILE(4) OVER (PARTITION BY ea.exam_id ORDER BY ea.score_percent) AS quartile
                FROM exam_attempts ea
                WHERE ea.status IN ('SUBMITTED', 'AUTO_SUBMITTED')
            )
            SELECT
                qre.item_id                           AS questionId,
                COUNT(DISTINCT qre.attempt_id)         AS totalAttempts,
                ROUND(SUM(CASE WHEN qre.is_correct THEN 1 ELSE 0 END)::numeric
                      / NULLIF(COUNT(*), 0) * 100, 2) AS correctRate,
                ROUND(AVG(qre.latency_ms), 0)          AS avgResponseTimeMs,
                COALESCE(
                    (SELECT ROUND(SUM(CASE WHEN qre2.is_correct THEN 1 ELSE 0 END)::numeric
                              / NULLIF(COUNT(*), 0) * 100, 2)
                     FROM question_review_events qre2
                     JOIN exam_stats es2 ON es2.attempt_id = qre2.attempt_id
                     WHERE qre2.item_id = :questionId AND es2.quartile = 4)
                    -
                    (SELECT ROUND(SUM(CASE WHEN qre3.is_correct THEN 1 ELSE 0 END)::numeric
                              / NULLIF(COUNT(*), 0) * 100, 2)
                     FROM question_review_events qre3
                     JOIN exam_stats es3 ON es3.attempt_id = qre3.attempt_id
                     WHERE qre3.item_id = :questionId AND es3.quartile = 1),
                    0.0
                )                                      AS discriminationIndex
            FROM question_review_events qre
            WHERE qre.item_id = :questionId
            GROUP BY qre.item_id
            """, nativeQuery = true)
    QuestionStatProjection findQuestionStatsWithDiscrimination(@Param("questionId") Long questionId);

    @Query(value = """
            SELECT
                ea.exam_id                        AS examId,
                COUNT(ea.id)                      AS totalAttempts,
                ROUND(AVG(ea.score_percent), 2)  AS avgScorePercent,
                ROUND(AVG(ea.duration_seconds), 0) AS avgDurationSeconds,
                COUNT(DISTINCT ea.user_id)        AS uniqueParticipants,
                ROUND(SUM(CASE WHEN ea.passed = true THEN 1 ELSE 0 END)::numeric
                      / NULLIF(COUNT(*), 0) * 100, 2) AS passRate
            FROM exam_attempts ea
            WHERE ea.exam_id = :examId
              AND ea.status IN ('SUBMITTED', 'AUTO_SUBMITTED')
            GROUP BY ea.exam_id
            """, nativeQuery = true)
    ExamStatProjection findExamStats(@Param("examId") Long examId);

    interface QuestionStatProjection {
        Long getQuestionId();

        Integer getTotalAttempts();

        Double getCorrectRate();

        Double getAvgResponseTimeMs();

        Double getDiscriminationIndex();
    }

    interface ExamStatProjection {
        Long getExamId();

        Integer getTotalAttempts();

        Double getAvgScorePercent();

        Double getAvgDurationSeconds();

        Integer getUniqueParticipants();

        Double getPassRate();
    }
}
