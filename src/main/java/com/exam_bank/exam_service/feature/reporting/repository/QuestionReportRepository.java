package com.exam_bank.exam_service.feature.reporting.repository;

import com.exam_bank.exam_service.feature.reporting.entity.QuestionReport;
import com.exam_bank.exam_service.feature.reporting.entity.ReportStatus;
import com.exam_bank.exam_service.feature.reporting.entity.ReportType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionReportRepository extends JpaRepository<QuestionReport, Long> {

        boolean existsByQuestionIdAndAttemptIdAndReporterId(Long questionId, Long attemptId, Long reporterId);

        @Query("""
                        SELECT report FROM QuestionReport report
                        JOIN FETCH report.question question
                        JOIN FETCH question.exam
                        JOIN FETCH report.attempt
                        WHERE report.reporterId = :reporterId
                        ORDER BY report.createdAt DESC
                        """)
        List<QuestionReport> findDetailedByReporterIdOrderByCreatedAtDesc(@Param("reporterId") Long reporterId);

        @Query("""
                        SELECT report FROM QuestionReport report
                        JOIN FETCH report.question question
                        JOIN FETCH question.exam
                        JOIN FETCH report.attempt
                        WHERE question.id = :questionId
                        ORDER BY report.createdAt DESC
                        """)
        List<QuestionReport> findDetailedByQuestionIdOrderByCreatedAtDesc(@Param("questionId") Long questionId);

        @Query("""
                        SELECT report FROM QuestionReport report
                        JOIN FETCH report.question question
                        JOIN FETCH question.exam
                        JOIN FETCH report.attempt
                        WHERE question.id = :questionId AND report.status IN :statuses
                        ORDER BY report.createdAt DESC
                        """)
        List<QuestionReport> findDetailedByQuestionIdAndStatusInOrderByCreatedAtDesc(
                        @Param("questionId") Long questionId,
                        @Param("statuses") List<ReportStatus> statuses);

        @Query(value = """
                        SELECT qr.question_id
                        FROM question_reports qr
                        WHERE qr.status IN ('REPORTED', 'REVIEWING')
                        GROUP BY qr.question_id
                        ORDER BY MAX(qr.created_at) DESC
                        """, countQuery = """
                        SELECT COUNT(DISTINCT qr.question_id)
                        FROM question_reports qr
                        WHERE qr.status IN ('REPORTED', 'REVIEWING')
                        """, nativeQuery = true)
        Page<Long> findQueuedQuestionIds(Pageable pageable);

        @Query(value = """
                        SELECT qr.question_id
                        FROM question_reports qr
                        JOIN questions q ON q.id = qr.question_id
                        JOIN online_exams exam ON exam.id = q.exam_id
                        WHERE qr.status IN ('REPORTED', 'REVIEWING')
                                AND exam.created_by = :createdBy
                        GROUP BY qr.question_id
                        ORDER BY MAX(qr.created_at) DESC
                        """, countQuery = """
                        SELECT COUNT(DISTINCT qr.question_id)
                        FROM question_reports qr
                        JOIN questions q ON q.id = qr.question_id
                        JOIN online_exams exam ON exam.id = q.exam_id
                        WHERE qr.status IN ('REPORTED', 'REVIEWING')
                                AND exam.created_by = :createdBy
                        """, nativeQuery = true)
        Page<Long> findQueuedQuestionIdsByExamCreator(@Param("createdBy") String createdBy, Pageable pageable);

        @Query(value = """
                        SELECT qr.question_id
                        FROM question_report_history qrh
                        JOIN question_reports qr ON qr.id = qrh.report_id
                        JOIN questions q ON q.id = qr.question_id
                        JOIN online_exams exam ON exam.id = q.exam_id
                        WHERE exam.created_by = :createdBy
                        GROUP BY qr.question_id
                        ORDER BY MAX(COALESCE(qrh.processed_at, qrh.created_at)) DESC
                        """, countQuery = """
                        SELECT COUNT(DISTINCT qr.question_id)
                        FROM question_report_history qrh
                        JOIN question_reports qr ON qr.id = qrh.report_id
                        JOIN questions q ON q.id = qr.question_id
                        JOIN online_exams exam ON exam.id = q.exam_id
                        WHERE exam.created_by = :createdBy
                        """, nativeQuery = true)
        Page<Long> findProcessedQuestionIdsByExamCreator(@Param("createdBy") String createdBy, Pageable pageable);

        @Query("""
                        SELECT report.reportType, COUNT(report)
                        FROM QuestionReport report
                        WHERE report.question.id = :questionId AND report.status IN :statuses
                        GROUP BY report.reportType
                        """)
        List<Object[]> countByQuestionIdAndStatusInGroupedType(@Param("questionId") Long questionId,
                        @Param("statuses") List<ReportStatus> statuses);

        long countByQuestionIdAndReportTypeAndStatusIn(Long questionId, ReportType reportType,
                        List<ReportStatus> statuses);

        long countByQuestionIdAndStatusIn(Long questionId, List<ReportStatus> statuses);

        @Query("""
                        SELECT COUNT(DISTINCT report.reporterId)
                        FROM QuestionReport report
                        WHERE report.question.id = :questionId AND report.status IN :statuses
                        """)
        long countDistinctReporterByQuestionIdAndStatusIn(@Param("questionId") Long questionId,
                        @Param("statuses") List<ReportStatus> statuses);

        @Query("select report.id from QuestionReport report where report.question.id in :questionIds")
        List<Long> findIdsByQuestionIdIn(@Param("questionIds") List<Long> questionIds);

        @Query("select report.id from QuestionReport report where report.attempt.id in :attemptIds")
        List<Long> findIdsByAttemptIdIn(@Param("attemptIds") List<Long> attemptIds);

        @Modifying
        @Query("delete from QuestionReport report where report.question.id in :questionIds")
        void deleteByQuestionIdIn(@Param("questionIds") List<Long> questionIds);

        @Modifying
        @Query("delete from QuestionReport report where report.attempt.id in :attemptIds")
        void deleteByAttemptIdIn(@Param("attemptIds") List<Long> attemptIds);
}
