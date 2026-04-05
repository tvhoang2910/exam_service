package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.ExamAttempt;
import com.exam_bank.exam_service.entity.ExamAttemptStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, Long> {
    @EntityGraph(attributePaths = { "exam", "exam.tags" })
    Optional<ExamAttempt> findByIdAndUserId(Long id, Long userId);

    Optional<ExamAttempt> findFirstByExamIdAndUserIdAndStatusOrderByCreatedAtDesc(Long examId, Long userId,
            ExamAttemptStatus status);

    @Query("""
            select attempt.id as attemptId,
               attempt.exam.id as examId,
               attempt.expiresAt as expiresAt
            from ExamAttempt attempt
            where attempt.id = :attemptId
              and attempt.userId = :userId
              and attempt.status = :status
            """)
    Optional<AttemptSaveContext> findSaveContext(Long attemptId, Long userId, ExamAttemptStatus status);

    long countByExamIdAndUserIdAndStatusIn(Long examId, Long userId, Collection<ExamAttemptStatus> statuses);

    long countByExamId(Long examId);

    @Query("select attempt.id from ExamAttempt attempt where attempt.exam.id = :examId")
    List<Long> findIdsByExamId(Long examId);

    @Modifying
    @Query("delete from ExamAttempt attempt where attempt.exam.id = :examId")
    void deleteByExamId(Long examId);

    @EntityGraph(attributePaths = { "exam", "exam.tags" })
    List<ExamAttempt> findByUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "exam", "exam.tags" })
    @Query("""
            select attempt
            from ExamAttempt attempt
            join fetch attempt.exam exam
            where attempt.userId = :userId
              and attempt.status in :statuses
            order by attempt.submittedAt desc, attempt.createdAt desc
            """)
    List<ExamAttempt> findSubmittedHistoryByUserId(Long userId, Collection<ExamAttemptStatus> statuses);

    interface AttemptSaveContext {
        Long getAttemptId();

        Long getExamId();

        Instant getExpiresAt();
    }
}
