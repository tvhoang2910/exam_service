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
            SELECT
                qre.item_id                       AS questionId,
                COUNT(DISTINCT qre.attempt_id)    AS totalAttempts,
                ROUND(SUM(CASE WHEN qre.is_correct = true THEN 1 ELSE 0 END)::numeric
                      / NULLIF(COUNT(*), 0) * 100, 2) AS correctRate,
                ROUND(AVG(qre.latency_ms), 0)    AS avgResponseTimeMs,
                -- discrimination: % top-group correct - % bottom-group correct
                (ROUND(SUM(CASE WHEN qre.is_correct = true
                                AND percentile_rank(qre.score_percent::real)
                                    OVER (PARTITION BY qre.attempt_id) >= 0.7
                                THEN 1 ELSE 0 END)::numeric
                      / NULLIF(SUM(CASE WHEN percentile_rank(qre.score_percent::real)
                                          OVER (PARTITION BY qre.attempt_id) >= 0.7
                                      THEN 1 ELSE 0 END), 0), 2)
                 -
                 ROUND(SUM(CASE WHEN qre.is_correct = true
                                AND percentile_rank(qre.score_percent::real)
                                    OVER (PARTITION BY qre.attempt_id) < 0.3
                                THEN 1 ELSE 0 END)::numeric
                      / NULLIF(SUM(CASE WHEN percentile_rank(qre.score_percent::real)
                                          OVER (PARTITION BY qre.attempt_id) < 0.3
                                      THEN 1 ELSE 0 END), 0), 2))
                AS discriminationIndex
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
