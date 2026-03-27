package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    java.util.Optional<Question> findByIdAndExamId(Long id, Long examId);

    List<Question> findByIdInAndExamId(List<Long> ids, Long examId);

    List<Question> findByExamIdOrderByIdAsc(Long examId);

    List<Question> findByExamIdInOrderByIdAsc(List<Long> examIds);

    @Modifying
    @Query("delete from Question question where question.exam.id = :examId")
    void deleteByExamId(Long examId);
}