package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.ExamAttemptAnswer;
import com.exam_bank.exam_service.entity.AnswerStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExamAttemptAnswerRepository extends JpaRepository<ExamAttemptAnswer, Long> {
    Optional<ExamAttemptAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    List<ExamAttemptAnswer> findByAttemptIdAndQuestionIdIn(Long attemptId, List<Long> questionIds);

    @EntityGraph(attributePaths = "question")
    List<ExamAttemptAnswer> findByAttemptIdOrderByQuestionIdAsc(Long attemptId);

    @EntityGraph(attributePaths = { "attempt", "attempt.exam", "question" })
    @Query("""
            select answer
            from ExamAttemptAnswer answer
            where answer.question.questionType = com.exam_bank.exam_service.entity.QuestionType.ESSAY
              and answer.status = :status
            order by answer.attempt.submittedAt asc, answer.createdAt asc
            """)
    List<ExamAttemptAnswer> findEssaySubmissionsByStatus(@Param("status") AnswerStatus status);

    @EntityGraph(attributePaths = { "attempt", "attempt.exam", "question" })
    @Query("""
            select answer
            from ExamAttemptAnswer answer
            where answer.id = :id
              and answer.question.questionType = com.exam_bank.exam_service.entity.QuestionType.ESSAY
            """)
    Optional<ExamAttemptAnswer> findEssaySubmissionById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select answer
            from ExamAttemptAnswer answer
            join fetch answer.attempt attempt
            join fetch attempt.exam exam
            join fetch answer.question question
            where answer.id = :id
              and question.questionType = com.exam_bank.exam_service.entity.QuestionType.ESSAY
            """)
    Optional<ExamAttemptAnswer> findEssaySubmissionForUpdate(@Param("id") Long id);

    @Modifying
    @Query("delete from ExamAttemptAnswer answer where answer.attempt.id in :attemptIds")
    void deleteByAttemptIdIn(List<Long> attemptIds);
}
