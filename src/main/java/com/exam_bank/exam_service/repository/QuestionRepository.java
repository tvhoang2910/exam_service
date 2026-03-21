package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByExamIdOrderByIdAsc(Long examId);

    List<Question> findByExamIdInOrderByIdAsc(List<Long> examIds);

    void deleteByExamId(Long examId);
}