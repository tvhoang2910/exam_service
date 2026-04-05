package com.exam_bank.exam_service.feature.reporting.repository;

import com.exam_bank.exam_service.feature.reporting.entity.QuestionReportHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionReportHistoryRepository extends JpaRepository<QuestionReportHistory, Long> {
    List<QuestionReportHistory> findByReportIdOrderByCreatedAtAsc(Long reportId);

    @Query("""
            SELECT history
            FROM QuestionReportHistory history
            JOIN history.report report
            WHERE report.question.id = :questionId
            ORDER BY history.processedAt DESC, history.createdAt DESC
            """)
    List<QuestionReportHistory> findByQuestionIdOrderByProcessedAtDesc(@Param("questionId") Long questionId);

    List<QuestionReportHistory> findByProcessedByOrderByCreatedAtDesc(Long userId);

    @Modifying
    @Query("delete from QuestionReportHistory history where history.report.id in :reportIds")
    void deleteByReportIdIn(@Param("reportIds") List<Long> reportIds);
}
