package com.exam_bank.exam_service.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.exam_bank.exam_service.entity.QuestionOption;

@Repository
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
    List<QuestionOption> findByQuestionIdInOrderByIdAsc(Collection<Long> questionIds);

    @Modifying(clearAutomatically = true)
    @Query("delete from QuestionOption option where option.question.id in :questionIds")
    void deleteByQuestionIdIn(Collection<Long> questionIds);
}