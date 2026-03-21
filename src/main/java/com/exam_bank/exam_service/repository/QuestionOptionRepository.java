package com.exam_bank.exam_service.repository;

import com.exam_bank.exam_service.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
    List<QuestionOption> findByQuestionIdInOrderByIdAsc(Collection<Long> questionIds);

    void deleteByQuestionIdIn(Collection<Long> questionIds);
}