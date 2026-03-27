package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.ExamAttemptAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExamAttemptAnswerRepository extends JpaRepository<ExamAttemptAnswer, Long> {
    Optional<ExamAttemptAnswer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);

    List<ExamAttemptAnswer> findByAttemptIdAndQuestionIdIn(Long attemptId, List<Long> questionIds);

    @EntityGraph(attributePaths = "question")
    List<ExamAttemptAnswer> findByAttemptIdOrderByQuestionIdAsc(Long attemptId);

    @Modifying
    @Query("delete from ExamAttemptAnswer answer where answer.attempt.id in :attemptIds")
    void deleteByAttemptIdIn(List<Long> attemptIds);
}
